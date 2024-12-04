import { useMutation, useQueryClient } from "@tanstack/react-query";
import get from "lodash/get";
import { useToast } from "@/components/ui/use-toast";
import api, { PROMPTS_REST_ENDPOINT } from "@/api/api";

type UsePromptBatchDeleteMutationParams = {
  ids: string[];
};

const usePromptBatchDeleteMutation = () => {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  return useMutation({
    mutationFn: async ({ ids }: UsePromptBatchDeleteMutationParams) => {
      const { data } = await api.post(`${PROMPTS_REST_ENDPOINT}delete`, {
        ids: ids,
      });
      return data;
    },
    onError: (error) => {
      const message = get(
        error,
        ["response", "data", "message"],
        error.message,
      );

      toast({
        title: "Error",
        description: message,
        variant: "destructive",
      });
    },
    onSettled: () => {
      return queryClient.invalidateQueries({
        queryKey: ["prompts"],
      });
    },
  });
};

export default usePromptBatchDeleteMutation;
