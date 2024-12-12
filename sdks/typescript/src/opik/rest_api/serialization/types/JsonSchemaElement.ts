/**
 * This file was auto-generated by Fern from our API Definition.
 */

import * as serializers from "../index";
import * as OpikApi from "../../api/index";
import * as core from "../../core";

export const JsonSchemaElement: core.serialization.ObjectSchema<
    serializers.JsonSchemaElement.Raw,
    OpikApi.JsonSchemaElement
> = core.serialization.object({
    type: core.serialization.string().optional(),
});

export declare namespace JsonSchemaElement {
    interface Raw {
        type?: string | null;
    }
}
