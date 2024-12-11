/**
 * This file was auto-generated by Fern from our API Definition.
 */
import * as serializers from "../index";
import * as OpikApi from "../../api/index";
import * as core from "../../core";
export declare const ResponseFormatType: core.serialization.Schema<serializers.ResponseFormatType.Raw, OpikApi.ResponseFormatType>;
export declare namespace ResponseFormatType {
    type Raw = "text" | "json_object" | "json_schema";
}
