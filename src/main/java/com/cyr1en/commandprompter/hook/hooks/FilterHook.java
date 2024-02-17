package com.cyr1en.commandprompter.hook.hooks;

import com.cyr1en.commandprompter.prompt.ui.HeadCache;

/**
 * A hook that registers filters to the head cache.
 */
public interface FilterHook {

    /**
     * Register filters to the head cache.
     *
     * @param headCache the head cache
     */
    void registerFilters(HeadCache headCache);
}
