/**
 * This file was auto-generated by Fern from our API Definition.
 */
import * as serializers from "../../../index";
import * as OpikApi from "../../../../api/index";
import * as core from "../../../../core";
export declare const ProjectMetricRequestPublicMetricType: core.serialization.Schema<serializers.ProjectMetricRequestPublicMetricType.Raw, OpikApi.ProjectMetricRequestPublicMetricType>;
export declare namespace ProjectMetricRequestPublicMetricType {
    type Raw = "FEEDBACK_SCORES" | "TRACE_COUNT" | "TOKEN_USAGE" | "DURATION" | "COST";
}
