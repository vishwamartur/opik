/**
 * This file was auto-generated by Fern from our API Definition.
 */

import * as OpikApi from "../../../../index";

/**
 * @example
 *     {}
 */
export interface ChatCompletionRequest {
    model?: string;
    messages?: OpikApi.Message[];
    temperature?: number;
    topP?: number;
    n?: number;
    stream?: boolean;
    streamOptions?: OpikApi.StreamOptions;
    stop?: string[];
    maxTokens?: number;
    maxCompletionTokens?: number;
    presencePenalty?: number;
    frequencyPenalty?: number;
    logitBias?: Record<string, number>;
    user?: string;
    responseFormat?: OpikApi.ResponseFormat;
    seed?: number;
    tools?: OpikApi.Tool[];
    toolChoice?: Record<string, unknown>;
    parallelToolCalls?: boolean;
    functions?: OpikApi.Function[];
    functionCall?: OpikApi.FunctionCall;
}
