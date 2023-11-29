//
// MIT License
//
// Copyright (c) 2022 Alexander Söderberg & Contributors
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
package cloud.commandframework.jda.parsers;

import cloud.commandframework.Description;
import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.arguments.suggestion.SuggestionProvider;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.context.CommandInput;
import cloud.commandframework.exceptions.parsing.NoInputProvidedException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apiguardian.api.API;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Command Argument for {@link net.dv8tion.jda.api.entities.Role}
 *
 * @param <C> Command sender type
 */
@SuppressWarnings("unused")
public final class RoleArgument<C> extends CommandArgument<C, Role> {

    private final Set<ParserMode> modes;

    private RoleArgument(
            final @NonNull String name,
            final @Nullable SuggestionProvider<C> suggestionProvider,
            final @NonNull Description defaultDescription,
            final @NonNull Set<ParserMode> modes
    ) {
        super(name, new RoleParser<>(modes), Role.class, suggestionProvider, defaultDescription);
        this.modes = modes;
    }

    /**
     * Create a new {@link Builder}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return new {@link Builder}
     * @since 1.8.0
     */
    @API(status = API.Status.STABLE, since = "1.8.0")
    public static <C> @NonNull Builder<C> builder(final @NonNull String name) {
        return new Builder<>(name);
    }

    /**
     * Create a new required command component
     *
     * @param name Component name
     * @param <C>  Command sender type
     * @return Created component
     */
    public static <C> @NonNull CommandArgument<C, Role> of(final @NonNull String name) {
        return RoleArgument.<C>builder(name).build();
    }

    /**
     * Get the modes enabled on the parser
     *
     * @return List of Modes
     */
    public @NotNull Set<ParserMode> getModes() {
        return this.modes;
    }


    public enum ParserMode {
        MENTION,
        ID,
        NAME
    }


    public static final class Builder<C> extends CommandArgument.TypedBuilder<C, Role, Builder<C>> {

        private Set<ParserMode> modes = new HashSet<>();

        private Builder(final @NonNull String name) {
            super(Role.class, name);
        }

        /**
         * Set the modes for the parsers to use
         *
         * @param modes List of Modes
         * @return Builder instance
         */
        public @NonNull Builder<C> withParsers(final @NonNull Set<ParserMode> modes) {
            this.modes = modes;
            return this;
        }

        /**
         * Builder a new example component
         *
         * @return Constructed component
         */
        @Override
        public @NonNull RoleArgument<C> build() {
            return new RoleArgument<>(
                    this.getName(),
                    this.suggestionProvider(),
                    this.getDefaultDescription(),
                    this.modes
            );
        }
    }


    public static final class RoleParser<C> implements ArgumentParser<C, Role> {

        private final Set<ParserMode> modes;

        /**
         * Construct a new argument parser for {@link Role}
         *
         * @param modes List of parsing modes to use when parsing
         * @throws IllegalStateException If no parsing modes were provided
         */
        public RoleParser(final @NonNull Set<ParserMode> modes) {
            if (modes.isEmpty()) {
                throw new IllegalArgumentException("At least one parsing mode is required");
            }

            this.modes = modes;
        }

        @Override
        public @NonNull ArgumentParseResult<Role> parse(
                final @NonNull CommandContext<C> commandContext,
                final @NonNull CommandInput commandInput
        ) {
            final String input = commandInput.peekString();
            if (input.isEmpty()) {
                return ArgumentParseResult.failure(new NoInputProvidedException(
                        RoleParser.class,
                        commandContext
                ));
            }

            if (!commandContext.contains("MessageReceivedEvent")) {
                return ArgumentParseResult.failure(new IllegalStateException(
                        "MessageReceivedEvent was not in the command context."
                ));
            }

            final MessageReceivedEvent event = commandContext.get("MessageReceivedEvent");
            Exception exception = null;

            if (!event.isFromGuild()) {
                return ArgumentParseResult.failure(new IllegalArgumentException("Role arguments can only be parsed in guilds"));
            }

            if (this.modes.contains(ParserMode.MENTION)) {
                if (input.startsWith("<@&") && input.endsWith(">")) {
                    final String id = input.substring(3, input.length() - 1);

                    try {
                        final ArgumentParseResult<Role> role = this.roleFromId(event, input, id);
                        commandInput.readString();
                        return role;
                    } catch (final RoleNotFoundException | NumberFormatException e) {
                        exception = e;
                    }
                } else {
                    exception = new IllegalArgumentException(
                            String.format("Input '%s' is not a role mention.", input)
                    );
                }
            }

            if (this.modes.contains(ParserMode.ID)) {
                try {
                    final ArgumentParseResult<Role> result = this.roleFromId(event, input, input);
                    commandInput.readString();
                    return result;
                } catch (final RoleNotFoundException | NumberFormatException e) {
                    exception = e;
                }
            }

            if (this.modes.contains(ParserMode.NAME)) {
                final List<Role> roles = event.getGuild().getRolesByName(input, true);

                if (roles.isEmpty()) {
                    exception = new RoleNotFoundException(input);
                } else if (roles.size() > 1) {
                    exception = new TooManyRolesFoundParseException(input);
                } else {
                    commandInput.readString();
                    return ArgumentParseResult.success(roles.get(0));
                }
            }

            assert exception != null;
            return ArgumentParseResult.failure(exception);
        }

        @Override
        public boolean isContextFree() {
            return true;
        }

        private @NonNull ArgumentParseResult<Role> roleFromId(
                final @NonNull MessageReceivedEvent event,
                final @NonNull String input,
                final @NonNull String id
        )
                throws RoleNotFoundException, NumberFormatException {
            final Role role = event.getGuild().getRoleById(id);

            if (role == null) {
                throw new RoleNotFoundException(input);
            }

            return ArgumentParseResult.success(role);
        }
    }


    public static class RoleParseException extends IllegalArgumentException {

        private static final long serialVersionUID = -2451548379508062135L;
        private final String input;

        /**
         * Construct a new role parse exception
         *
         * @param input String input
         */
        public RoleParseException(final @NonNull String input) {
            this.input = input;
        }

        /**
         * Get the users input
         *
         * @return users input
         */
        public final @NonNull String getInput() {
            return this.input;
        }
    }


    public static final class TooManyRolesFoundParseException extends RoleParseException {

        private static final long serialVersionUID = -8604082973199995006L;

        /**
         * Construct a new role parse exception
         *
         * @param input String input
         */
        public TooManyRolesFoundParseException(final @NonNull String input) {
            super(input);
        }

        @Override
        public @NonNull String getMessage() {
            return String.format("Too many roles found for '%s'.", getInput());
        }
    }


    public static final class RoleNotFoundException extends RoleParseException {

        private static final long serialVersionUID = 7931804739792920510L;

        /**
         * Construct a new role parse exception
         *
         * @param input String input
         */
        public RoleNotFoundException(final @NonNull String input) {
            super(input);
        }

        @Override
        public @NonNull String getMessage() {
            return String.format("Role not found for '%s'.", getInput());
        }
    }
}
