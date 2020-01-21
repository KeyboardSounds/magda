import express from "express";

import { MinionArguments } from "./commonYargs";
import {
    Record,
    AspectDefinition
} from "magda-typescript-common/src/generated/registry/api";
import Registry from "magda-typescript-common/src/registry/AuthorizedRegistryClient";

export default class MinionOptions {
    argv: MinionArguments;
    id: string;
    aspects: string[];
    optionalAspects: string[];
    writeAspectDefs: AspectDefinition[];
    async?: boolean;
    onRecordFound: (record: Record, registry: Registry) => Promise<void>;
    express?: () => express.Express;
    maxRetries?: number;
    concurrency?: number;
}
