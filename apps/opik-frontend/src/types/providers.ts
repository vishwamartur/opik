export enum PROVIDER_TYPE {
  OPEN_AI = "openai",
}

export interface ProviderKey {
  id: string;
  keyName: string;
  created_at: string;
  // ALEX
  provider: PROVIDER_TYPE;
}

export interface ProviderKeyWithAPIKey extends ProviderKey {
  apiKey: string;
}
