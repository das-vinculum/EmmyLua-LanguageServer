package com.intellij.psi.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;

public class CachedValuesManager {

    public static CachedValuesManager instance = new CachedValuesManager();

    public static CachedValuesManager getManager(Project project) {
        return instance;
    }

    public <T, D extends UserDataHolder, P> T getParameterizedCachedValue(@NotNull D dataHolder,
                                                                          @NotNull Key<ParameterizedCachedValue<T,P>> key,
                                                                          @NotNull ParameterizedCachedValueProvider<T, P> provider,
                                                                          boolean trackValue,
                                                                          P parameter) {
        return provider.compute(parameter).getValue();
    }
}
