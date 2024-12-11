/**
 * This file was auto-generated by Fern from our API Definition.
 */
import * as environments from "./environments";
import * as core from "./core";
import { SystemUsage } from "./api/resources/systemUsage/client/Client";
import { Check } from "./api/resources/check/client/Client";
import { ChatCompletions } from "./api/resources/chatCompletions/client/Client";
import { Datasets } from "./api/resources/datasets/client/Client";
import { Experiments } from "./api/resources/experiments/client/Client";
import { FeedbackDefinitions } from "./api/resources/feedbackDefinitions/client/Client";
import { LlmProviderKey } from "./api/resources/llmProviderKey/client/Client";
import { Projects } from "./api/resources/projects/client/Client";
import { Prompts } from "./api/resources/prompts/client/Client";
import { Spans } from "./api/resources/spans/client/Client";
import { Traces } from "./api/resources/traces/client/Client";
export declare namespace OpikApiClient {
    interface Options {
        environment?: core.Supplier<environments.OpikApiEnvironment | string>;
    }
    interface RequestOptions {
        /** The maximum time to wait for a response in seconds. */
        timeoutInSeconds?: number;
        /** The number of times to retry the request. Defaults to 2. */
        maxRetries?: number;
        /** A hook to abort the request. */
        abortSignal?: AbortSignal;
        /** Additional headers to include in the request. */
        headers?: Record<string, string>;
    }
}
export declare class OpikApiClient {
    protected readonly _options: OpikApiClient.Options;
    constructor(_options?: OpikApiClient.Options);
    /**
     * @param {OpikApiClient.RequestOptions} requestOptions - Request-specific configuration.
     *
     * @example
     *     await client.isAlive()
     */
    isAlive(requestOptions?: OpikApiClient.RequestOptions): core.APIPromise<unknown>;
    /**
     * @param {OpikApiClient.RequestOptions} requestOptions - Request-specific configuration.
     *
     * @example
     *     await client.version()
     */
    version(requestOptions?: OpikApiClient.RequestOptions): core.APIPromise<unknown>;
    protected _systemUsage: SystemUsage | undefined;
    get systemUsage(): SystemUsage;
    protected _check: Check | undefined;
    get check(): Check;
    protected _chatCompletions: ChatCompletions | undefined;
    get chatCompletions(): ChatCompletions;
    protected _datasets: Datasets | undefined;
    get datasets(): Datasets;
    protected _experiments: Experiments | undefined;
    get experiments(): Experiments;
    protected _feedbackDefinitions: FeedbackDefinitions | undefined;
    get feedbackDefinitions(): FeedbackDefinitions;
    protected _llmProviderKey: LlmProviderKey | undefined;
    get llmProviderKey(): LlmProviderKey;
    protected _projects: Projects | undefined;
    get projects(): Projects;
    protected _prompts: Prompts | undefined;
    get prompts(): Prompts;
    protected _spans: Spans | undefined;
    get spans(): Spans;
    protected _traces: Traces | undefined;
    get traces(): Traces;
}
