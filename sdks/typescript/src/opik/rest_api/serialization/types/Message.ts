/**
 * This file was auto-generated by Fern from our API Definition.
 */

import * as serializers from "../index";
import * as OpikApi from "../../api/index";
import * as core from "../../core";

export const Message: core.serialization.Schema<serializers.Message.Raw, OpikApi.Message> = core.serialization.record(
    core.serialization.string(),
    core.serialization.unknown()
);

export declare namespace Message {
    type Raw = Record<string, unknown>;
}
