/**
 * This file was auto-generated by Fern from our API Definition.
 */

import * as serializers from "../index";
import * as OpikApi from "../../api/index";
import * as core from "../../core";
import { AssistantMessage } from "./AssistantMessage";
import { Delta } from "./Delta";

export const ChatCompletionChoice: core.serialization.ObjectSchema<
    serializers.ChatCompletionChoice.Raw,
    OpikApi.ChatCompletionChoice
> = core.serialization.object({
    index: core.serialization.number().optional(),
    message: AssistantMessage.optional(),
    delta: Delta.optional(),
    finishReason: core.serialization.property("finish_reason", core.serialization.string().optional()),
});

export declare namespace ChatCompletionChoice {
    interface Raw {
        index?: number | null;
        message?: AssistantMessage.Raw | null;
        delta?: Delta.Raw | null;
        finish_reason?: string | null;
    }
}
