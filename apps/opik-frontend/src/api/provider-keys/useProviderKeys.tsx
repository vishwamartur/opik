import { QueryFunctionContext, useQuery } from "@tanstack/react-query";
import api, { PROVIDER_KEYS_REST_ENDPOINT, QueryConfig } from "@/api/api";
import { ProviderKey } from "@/types/providers";

type UseProviderKeysListParams = {
  workspaceName: string;
  search?: string;
  page: number;
  size: number;
};

type UseProviderKeysListResponse = {
  content: ProviderKey[];
  total: number;
};

const getProviderKeys = async (
  { signal }: QueryFunctionContext,
  { search, size, page }: UseProviderKeysListParams,
) => {
  const { data } = await api.get(PROVIDER_KEYS_REST_ENDPOINT, {
    signal,
    params: {
      ...(search && { name: search }),
      size,
      page,
    },
  });

  return data;
};

export default function useProviderKeys(
  params: UseProviderKeysListParams,
  options?: QueryConfig<UseProviderKeysListResponse>,
) {
  return useQuery({
    // ALEX MAKE A KEY OUT OF IT
    queryKey: ["providerKeys", params],
    queryFn: (context) => getProviderKeys(context, params),
    ...options,
  });
}
