import React, { useEffect, useMemo, useRef, useState } from "react";
import SearchInput from "@/components/shared/SearchInput/SearchInput";
import { Button } from "@/components/ui/button";
import {
  COLUMN_NAME_ID,
  COLUMN_SELECT_ID,
  COLUMN_TYPE,
  ColumnData,
} from "@/types/shared";
import { ColumnPinningState } from "@tanstack/react-table";

import { convertColumnDataToColumn } from "@/lib/table";
import { ProviderKey } from "@/types/providers";
import useProviderKeys from "@/api/provider-keys/useProviderKeys";
import useAppStore from "@/store/AppStore";
import AddEditAIProviderDialog from "@/components/pages/ConfigurationPage/AIProvidersTab/AddEditAIProviderDialog";
import DataTable from "@/components/shared/DataTable/DataTable";
import DataTableNoData from "@/components/shared/DataTableNoData/DataTableNoData";
import { formatDate } from "@/lib/date";
import { PROVIDERS } from "@/constants/providers";
import AIProviderCell from "@/components/pages/ConfigurationPage/AIProvidersTab/AIProviderCell";
import DataTablePagination from "@/components/shared/DataTablePagination/DataTablePagination";
import { generateActionsColumDef } from "@/components/shared/DataTable/utils";
import { ProjectRowActionsCell } from "@/components/pages/ProjectsPage/ProjectRowActionsCell";
import AIProvidersRowActionsCell from "@/components/pages/ConfigurationPage/AIProvidersTab/AIProvidersRowActionsCell";

export const DEFAULT_COLUMNS: ColumnData<ProviderKey>[] = [
  {
    id: COLUMN_NAME_ID,
    label: "Name",
    type: COLUMN_TYPE.string,
    accessorFn: (row) => PROVIDERS[row.provider]?.apiKeyName,
  },
  {
    id: "created_at",
    label: "Created",
    type: COLUMN_TYPE.time,
    accessorFn: (row) => formatDate(row.created_at),
  },
  {
    id: "provider",
    label: "Provider",
    type: COLUMN_TYPE.string,
    cell: AIProviderCell as never,
  },
];

export const DEFAULT_COLUMN_PINNING: ColumnPinningState = {
  left: [COLUMN_SELECT_ID, COLUMN_NAME_ID],
  right: [],
};

// ALEX RENAME AI
const AIProvidersTab = () => {
  const workspaceName = useAppStore((state) => state.activeWorkspaceName);

  const resetDialogKeyRef = useRef(0);
  const [openDialog, setOpenDialog] = useState<boolean>(false);

  const [search, setSearch] = useState("");
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);

  // ALEX CHECK CONFIGS
  const { data } = useProviderKeys({
    workspaceName,
    page,
    size,
    search,
  });

  const providerKeys = useMemo(() => data?.content ?? [], [data?.content]);
  const total = data?.total ?? 0;

  const columns = useMemo(() => {
    return [
      ...convertColumnDataToColumn<ProviderKey, ProviderKey>(
        DEFAULT_COLUMNS,
        {},
      ),
      generateActionsColumDef({
        cell: AIProvidersRowActionsCell,
      }),
    ];
  }, []);

  const handleAddConfigurationClick = () => {
    resetDialogKeyRef.current += 1;
    setOpenDialog(true);
  };

  return (
    <>
      <div>
        <div className="flex w-full items-center justify-between mb-4">
          <SearchInput
            setSearchText={setSearch}
            searchText={search}
            className="w-72"
          />
          <Button onClick={handleAddConfigurationClick}>
            Add configuration
          </Button>
        </div>

        <DataTable
          columns={columns}
          data={providerKeys}
          columnPinning={DEFAULT_COLUMN_PINNING}
          noData={
            // ALEX
            <DataTableNoData title="">
              {
                <Button variant="link" onClick={console.log}>
                  Create new prompt
                </Button>
              }
            </DataTableNoData>
          }
        />
        <div className="py-4">
          <DataTablePagination
            page={page}
            pageChange={setPage}
            size={size}
            sizeChange={setSize}
            total={total}
          />
        </div>
      </div>
      <AddEditAIProviderDialog
        key={resetDialogKeyRef.current}
        open={openDialog}
        setOpen={setOpenDialog}
      />
    </>
  );
};

export default AIProvidersTab;
