import React, { useCallback, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { ProviderKey, PROVIDER_TYPE } from "@/types/providers";
import useProviderKeysUpdateMutation from "@/api/provider-keys/useProviderKeysUpdateMutation";
import useProviderKeysCreateMutation from "@/api/provider-keys/useProviderKeysCreateMutation";
import SelectBox from "@/components/shared/SelectBox/SelectBox";
import { PROVIDERS, PROVIDERS_OPTIONS } from "@/constants/providers";
import { Input } from "@/components/ui/input";

type AddEditAIProviderDialogProps = {
  providerKey?: ProviderKey;
  open: boolean;
  setOpen: (open: boolean) => void;
};

const AddEditAIProviderDialog: React.FC<AddEditAIProviderDialogProps> = ({
  providerKey,
  open,
  setOpen,
}) => {
  const { mutate: createMutate } = useProviderKeysCreateMutation();
  const { mutate: updateMutate } = useProviderKeysUpdateMutation();
  const [provider, setProvider] = useState<PROVIDER_TYPE | "">(
    providerKey?.provider || "",
  );
  const [apiKey, setApiKey] = useState("");

  const isEdit = Boolean(providerKey);
  const isValid = Boolean(apiKey.length);

  const title = isEdit
    ? "Edit AI provider configuration"
    : "Add AI provider configuration";

  const buttonText = isEdit ? "Update configuration" : "Save configuration";

  const apiKeyLabel = provider
    ? `${PROVIDERS[provider]?.label} API Key`
    : "API Key";

  const submitHandler = useCallback(() => {
    if (isEdit) {
      updateMutate({
        providerKey: {
          id: providerKey!.id,
          apiKey,
        },
      });
    } else if (provider) {
      createMutate({
        providerKey: {
          apiKey,
          provider,
        },
      });
    }
  }, [createMutate, isEdit, apiKey, updateMutate, provider]);

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent className="max-w-lg sm:max-w-[560px]">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>
        <div className="flex flex-col gap-2 pb-4">
          <Label>Provider</Label>
          <SelectBox
            value={provider}
            onChange={(v) => setProvider(v as PROVIDER_TYPE)}
            options={PROVIDERS_OPTIONS}
          />
        </div>
        <div className="flex flex-col gap-2 pb-4">
          {/*ALEX*/}
          <Label htmlFor="apiKey">{apiKeyLabel}</Label>
          <Input
            id="apiKey"
            placeholder={apiKeyLabel}
            value={apiKey}
            onChange={(e) => setApiKey(e.target.value)}
          />
        </div>
        <DialogFooter>
          <DialogClose asChild>
            <Button variant="outline">Cancel</Button>
          </DialogClose>
          <DialogClose asChild>
            <Button type="submit" disabled={!isValid} onClick={submitHandler}>
              {buttonText}
            </Button>
          </DialogClose>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default AddEditAIProviderDialog;
