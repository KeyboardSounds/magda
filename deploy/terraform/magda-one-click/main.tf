provider "google-beta" {
  version     = ">= 2.11.0"
  project     = var.project
  region      = var.region
  credentials = "${var.credential_file_path}"
}

provider "google" {
  version     = ">= 2.11.0"
  project     = var.project
  region      = var.region
  credentials = "${var.credential_file_path}"
}

locals {
  kube_cert_json = "{\"apiVersion\":\"networking.gke.io/v1beta1\",\"kind\":\"ManagedCertificate\",\"metadata\":{\"name\":\"magda-certificate\"},\"spec\":{\"domains\":[\"${module.external_ip.address}.xip.io\"]}}"
  ingress_json   = "{\"apiVersion\":\"extensions/v1beta1\",\"kind\":\"Ingress\",\"metadata\":{\"name\":\"magda-ingress\",\"annotations\":{\"kubernetes.io/ingress.global-static-ip-name\":\"${module.external_ip.address}.xip.io\",\"networking.gke.io/managed-certificates\":\"magda-certificate\"}},\"spec\":{\"backend\":{\"serviceName\":\"gateway\",\"servicePort\":\"http\"}}}"
}

terraform {
  # The modules used in this example have been updated with 0.12 syntax, which means the example is no longer
  # compatible with any versions below 0.12.
  required_version = ">= 0.12"
}

/*
data "google_container_cluster" "cluster_info" {
  name     = module.cluster.cluster_name
  location = var.region
  project  = var.project

  depends_on = [
    module.cluster
  ]
}*/

data "google_client_config" "default" {}

provider "kubernetes" {
  # we use local .kube config that will be setup by the null-resource block below
  # oAuth2 token, cluster endpoint will be generated by `glcoud`
  load_config_file = false

  host                   = "https://${module.cluster.endpoint}"
  token                  = "${data.google_client_config.default.access_token}"
  cluster_ca_certificate = "${base64decode(module.cluster.master_auth.0.cluster_ca_certificate)}"
}

module "external_ip" {
  source = "../google-reserved-ip"
}

module "cluster" {
  source  = "../google-cluster"
  project = var.project
  region  = var.region
}

resource "kubernetes_cluster_role_binding" "default_service_acc_role_binding" {
  metadata {
    name = "default-service-acc-role-binding"
  }
  role_ref {
    kind      = "ClusterRole"
    name      = "cluster-admin"
    api_group = "rbac.authorization.k8s.io"
  }
  subject {
    kind      = "ServiceAccount"
    name      = "default"
    namespace = "kube-system"
  }

  depends_on = [
    module.cluster
  ]
}

resource "kubernetes_namespace" "magda_namespace" {
  metadata {
    name = "${var.namespace}"
  }
  depends_on = [
    kubernetes_cluster_role_binding.default_service_acc_role_binding
  ]
}

provider "helm" {
  kubernetes {
    load_config_file = false

    host                   = "https://${module.cluster.endpoint}"
    token                  = "${data.google_client_config.default.access_token}"
    cluster_ca_certificate = "${base64decode(module.cluster.master_auth.0.cluster_ca_certificate)}"
  }
}

resource "helm_release" "magda_helm_release" {
  name = "magda"
  # or repository = "../../helm" for local repo
  repository = "https://charts.magda.io/"
  chart      = "magda"
  timeout    = 1800

  namespace = "${var.namespace}"

  values = [
    "${file("../../helm/magda-one-click.yml")}"
  ]

  set {
    name  = "externalUrl"
    value = "http://${module.external_ip.address}.xip.io/"
  }

  depends_on = [
    kubernetes_cluster_role_binding.default_service_acc_role_binding
  ]
}

# We need this hack only because k8s terraform provider can't support the latest API
resource "null_resource" "after_helm_deployment" {
  # When to trigger the cmd
  depends_on = [
    helm_release.magda_helm_release
  ]

  provisioner "local-exec" {
    command    = "gcloud auth activate-service-account --key-file=${var.credential_file_path}"
    on_failure = "fail"
  }

  provisioner "local-exec" {
    command    = "gcloud config set project ${var.project}"
    on_failure = "fail"
  }

  provisioner "local-exec" {
    command    = "gcloud container clusters get-credentials ${module.cluster.cluster_name} --zone ${var.region} --project ${var.project}"
    on_failure = "fail"
  }

  provisioner "local-exec" {
    command    = "echo '${local.kube_cert_json}' | kubectl apply --namespace ${var.namespace} -f -"
    on_failure = "fail"
  }

  provisioner "local-exec" {
    command    = "echo '${local.ingress_json}' | kubectl apply --namespace ${var.namespace} -f -"
    on_failure = "fail"
  }

}

