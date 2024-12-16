import {
  ProviderMessageType,
  PLAYGROUND_MESSAGE_ROLE,
  PLAYGROUND_MODEL,
  PlaygroundMessageType,
  PlaygroundPromptConfigsType,
  PlaygroundOpenAIConfigsType,
} from "@/types/playground";
import { generateRandomString } from "@/lib/utils";
import {
  DEFAULT_OPEN_AI_CONFIGS,
  PLAYGROUND_MODELS,
} from "@/constants/playground";
import { PROVIDER_TYPE } from "@/types/providers";

export const generateDefaultPlaygroundPromptMessage = (
  message: Partial<PlaygroundMessageType> = {},
): PlaygroundMessageType => {
  return {
    content: "",
    role: PLAYGROUND_MESSAGE_ROLE.system,
    ...message,
    id: generateRandomString(),
  };
};

export const getModelProvider = (
  modelName: PLAYGROUND_MODEL,
): PROVIDER_TYPE | "" => {
  const provider = Object.entries(PLAYGROUND_MODELS).find(
    ([providerName, providerModels]) => {
      if (providerModels.find((pm) => modelName === pm.value)) {
        return providerName;
      }

      return false;
    },
  );

  if (!provider) {
    return "";
  }

  const [providerName] = provider;

  return providerName as PROVIDER_TYPE;
};

export const getDefaultConfigByProvider = (
  provider: PROVIDER_TYPE,
): PlaygroundPromptConfigsType => {
  if (provider === PROVIDER_TYPE.OPEN_AI) {
    return {
      temperature: DEFAULT_OPEN_AI_CONFIGS.TEMPERATURE,
      maxTokens: DEFAULT_OPEN_AI_CONFIGS.MAX_TOKENS,
      topP: DEFAULT_OPEN_AI_CONFIGS.TOP_P,
      stop: DEFAULT_OPEN_AI_CONFIGS.STOP,
      frequencyPenalty: DEFAULT_OPEN_AI_CONFIGS.FREQUENCY_PENALTY,
      presencePenalty: DEFAULT_OPEN_AI_CONFIGS.PRESENCE_PENALTY,
    } as PlaygroundOpenAIConfigsType;
  }
  return {};
};

export const transformMessageIntoProviderMessage = (
  message: PlaygroundMessageType,
): ProviderMessageType => {
  return {
    role: message.role,
    content: message.content,
  };
};
