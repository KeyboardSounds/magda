import React from "react";
import Editor from "./Editors/Editor";
import "./Style.scss";

interface ToggleEditorProps {
    value: any;
    onChange: Function;
    editor: Editor;
    enabled?: boolean;
}

/**
 * Will toggle between editing and viewing with an edit button.
 * Will always show if enabled is false.
 * Can specify custom viewer by specifying children which will be rendered instead of viewer of the editor.
 * Interchangable with AlwaysEditor.
 */
export class ToggleEditor extends React.Component<ToggleEditorProps> {
    state = {
        value: undefined,
        isEditing: false
    };

    change(value) {
        this.updateState({ value });
    }

    save(event: React.MouseEvent<HTMLButtonElement>) {
        if (this.props.value !== this.state.value) {
            this.props.onChange(this.state.value);
        }
        this.updateState({ isEditing: false });
    }

    edit(event: React.MouseEvent<HTMLButtonElement>) {
        this.updateState({ isEditing: true });
    }

    cancel(event: React.MouseEvent<HTMLButtonElement>) {
        this.updateState({ isEditing: false, value: this.props.value });
    }

    updateState(update: any) {
        this.setState((state, props) => Object.assign({}, state, update));
    }

    componentDidCatch(error, info) {
        // You can also log the error to an error reporting service
        console.error(error);
    }

    render() {
        let { value } = this.state;
        let { editor, enabled } = this.props;
        if (value === undefined) {
            value = this.props.value;
        }

        enabled = enabled === undefined || enabled;
        const isEditing = enabled && this.state.isEditing;

        return (
            <React.Fragment>
                {isEditing ? (
                    <React.Fragment>
                        {editor.edit(value, this.change.bind(this))}
                        {this.state.value !== undefined &&
                            this.props.value !== this.state.value && (
                                <button
                                    className="edit-button"
                                    onClick={this.save.bind(this)}
                                >
                                    &#128428;
                                </button>
                            )}
                        <button
                            className="edit-button"
                            onClick={this.cancel.bind(this)}
                        >
                            &#215;
                        </button>
                    </React.Fragment>
                ) : (
                    <React.Fragment>
                        {this.props.children
                            ? this.props.children
                            : editor.view(value)}{" "}
                        {enabled && (
                            <button
                                className="edit-button"
                                title="Edit data item"
                                onClick={this.edit.bind(this)}
                            >
                                &#9998;
                            </button>
                        )}
                    </React.Fragment>
                )}
            </React.Fragment>
        );
    }
}
