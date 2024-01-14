//
// MIT License
//
// Copyright (c) 2024 Incendo
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
package org.incendo.cloud.discord.jda5;

import cloud.commandframework.execution.postprocessor.CommandPostprocessingContext;
import cloud.commandframework.execution.postprocessor.CommandPostprocessor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.discord.slash.DiscordSetting;

final class ReplyCommandPostprocessor<C> implements CommandPostprocessor<C> {

    private final JDA5CommandManager<C> commandManager;

    ReplyCommandPostprocessor(final @NonNull JDA5CommandManager<C> commandManager) {
        this.commandManager = commandManager;
    }

    @Override
    public void accept(final @NonNull CommandPostprocessingContext<C> context) {
        final JDAInteraction interaction = context.commandContext().get(JDA5CommandManager.CONTEXT_JDA_INTERACTION);

        final ReplySetting<?> fallbackSetting;
        if (this.commandManager.discordSettings().get(DiscordSetting.FORCE_DEFER_NON_EPHEMERAL)) {
            fallbackSetting = ReplySetting.defer(false);
        } else if (this.commandManager.discordSettings().get(DiscordSetting.FORCE_DEFER_EPHEMERAL)) {
            fallbackSetting = ReplySetting.defer(true);
        } else {
            fallbackSetting = ReplySetting.doNotDefer();
        }

        final ReplySetting<?> replySetting = context.command().commandMeta().getOrDefault(
                JDA5CommandManager.META_REPLY_SETTING,
                fallbackSetting
        );
        if (replySetting.defer()) {
            interaction.replyCallback().deferReply(replySetting.ephemeral()).queue();
        }
        // This way we can keep track of whether we deferred or not.
        context.commandContext().store(JDA5CommandManager.META_REPLY_SETTING, replySetting);
    }
}