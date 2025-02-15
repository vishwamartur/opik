/**
 * This file was auto-generated by Fern from our API Definition.
 */
import * as serializers from "../index";
import * as OpikApi from "../../api/index";
import * as core from "../../core";
import { DatasetItemPublic } from "./DatasetItemPublic";
import { ColumnPublic } from "./ColumnPublic";
export declare const DatasetItemPagePublic: core.serialization.ObjectSchema<serializers.DatasetItemPagePublic.Raw, OpikApi.DatasetItemPagePublic>;
export declare namespace DatasetItemPagePublic {
    interface Raw {
        content?: DatasetItemPublic.Raw[] | null;
        page?: number | null;
        size?: number | null;
        total?: number | null;
        columns?: ColumnPublic.Raw[] | null;
    }
}
