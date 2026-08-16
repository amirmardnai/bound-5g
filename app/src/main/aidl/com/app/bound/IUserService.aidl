package com.app.bound;

interface IUserService {
    String setNetworkMode(int subId, String mode);
    String resetToDefaultNetworkMode(int subId);
    int getDefaultDataSubId();
    int[] getAvailableSubIds();
    boolean launchShellActivity(String componentName);
    void destroy();
}
