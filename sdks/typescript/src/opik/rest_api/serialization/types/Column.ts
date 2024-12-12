/**
 * This file was auto-generated by Fern from our API Definition.
 */

import * as serializers from "../index";
import * as OpikApi from "../../api/index";
import * as core from "../../core";
import { ColumnTypesItem } from "./ColumnTypesItem";

export const Column: core.serialization.ObjectSchema<serializers.Column.Raw, OpikApi.Column> =
    core.serialization.object({
        name: core.serialization.string().optional(),
        types: core.serialization.list(ColumnTypesItem).optional(),
        filterFieldPrefix: core.serialization.property("filter_field_prefix", core.serialization.string().optional()),
        filterField: core.serialization.string().optional(),
    });

export declare namespace Column {
    interface Raw {
        name?: string | null;
        types?: ColumnTypesItem.Raw[] | null;
        filter_field_prefix?: string | null;
        filterField?: string | null;
    }
}
