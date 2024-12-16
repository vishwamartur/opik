import React from "react";
import AIProvidersTab from "@/components/pages/ConfigurationPage/AIProvidersTab/AIProvidersTab";

const ConfigurationPage = () => {
  return (
    <div className="pt-6">
      <h1 className="comet-title-l">Configuration</h1>

      <div>
        <AIProvidersTab />
      </div>
    </div>
  );
};

export default ConfigurationPage;
