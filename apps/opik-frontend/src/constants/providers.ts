import OpenAIIcon from "@/icons/integrations/openai.svg?react";
import { PROVIDER_TYPE } from "@/types/providers";

export const PROVIDERS = {
  [PROVIDER_TYPE.OPEN_AI]: {
    label: "OpenAI",
    value: PROVIDER_TYPE.OPEN_AI,
    icon: OpenAIIcon,
    apiKeyName: "OPENAI_API_KEY",
  },
};

export const PROVIDERS_OPTIONS = Object.values(PROVIDERS);
