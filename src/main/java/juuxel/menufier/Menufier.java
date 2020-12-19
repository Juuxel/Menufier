/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package juuxel.menufier;

import net.fabricmc.mapping.reader.v2.TinyMetadata;
import net.fabricmc.mapping.tree.*;
import org.jetbrains.annotations.Nullable;
import org.organicdesign.fp.tuple.Tuple2;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.organicdesign.fp.StaticImports.map;

@CommandLine.Command(mixinStandardHelpOptions = true)
public final class Menufier implements Callable<@Nullable Void> {
    @CommandLine.Parameters(index = "0", description = "the input mapping file", arity = "1")
    private Path input;

    @CommandLine.Parameters(index = "1", description = "the output mapping file", arity = "1")
    private Path output;

    @CommandLine.Option(names = {"-n", "--namespace"}, description = "the namespace to process")
    private String namespace = "named";

    private Menufier() {
    }

    private static Map.Entry<Pattern, UnaryOperator<String>> replace(final String from, final String to) {
        return Tuple2.of(Pattern.compile(".*" + from + ".*"), name -> name.replace(from, to));
    }

    @Override
    public @Nullable Void call() throws Exception {
        final TinyTree inputTree;

        try (final BufferedReader reader = Files.newBufferedReader(input)) {
            inputTree = TinyMappingFactory.loadWithDetection(reader);
        }

        final Map<Pattern, UnaryOperator<String>> patterns = map(
                replace("ScreenHandler", "Menu"),
                replace("screenHandler", "menu"),
                replace("HandledScreen", "MenuScreen"),
                replace("SCREEN_HANDLER", "MENU"), // Registry
                replace("net/minecraft/screen", "net/minecraft/menu")
        );
        final Replacer replacer = new Replacer("named", patterns);
        final TinyTree outputTree = replacer.process(inputTree);

        writeTree(outputTree, output);

        return null;
    }

    private static void writeTree(final TinyTree outputTree, final Path targetPath) throws IOException {
        final List<String> lines = new ArrayList<>();

        final TinyMetadata metadata = outputTree.getMetadata();
        final List<String> namespaces = metadata.getNamespaces();
        final String defaultNamespace = namespaces.get(0);

        lines.add(String.format("tiny\t%d\t%d\t%s", metadata.getMajorVersion(), metadata.getMinorVersion(), String.join("\t", namespaces)));
        metadata.getProperties().forEach((key, value) -> lines.add(String.format("\t%s\t%s", key, value)));

        for (final ClassDef cDef : outputTree.getClasses()) {
            final String classNames = namespaces.stream().map(cDef::getName).collect(Collectors.joining("\t"));
            lines.add("c\t" + classNames);

            if (cDef.getComment() != null) {
                lines.add("\tc\t" + cDef.getComment());
            }

            for (final FieldDef fDef : cDef.getFields()) {
                final String fieldDescriptor = fDef.getDescriptor(defaultNamespace);
                final String fieldNames = namespaces.stream().map(fDef::getName).collect(Collectors.joining("\t"));
                lines.add("\tf\t" + fieldDescriptor + "\t" + fieldNames);

                if (fDef.getComment() != null) {
                    lines.add("\t\tc\t" + fDef.getComment());
                }
            }

            for (final MethodDef mDef : cDef.getMethods()) {
                final String methodDescriptor = mDef.getDescriptor(defaultNamespace);
                final String methodNames = namespaces.stream().map(mDef::getName).collect(Collectors.joining("\t"));
                lines.add("\tm\t" + methodDescriptor + "\t" + methodNames);

                if (mDef.getComment() != null) {
                    lines.add("\t\tc\t" + mDef.getComment());
                }

                for (final ParameterDef pDef : mDef.getParameters()) {
                    final String parameterNames = namespaces.stream().map(pDef::getName).collect(Collectors.joining("\t"));
                    final int lvIndex = pDef.getLocalVariableIndex();
                    lines.add("\t\tp\t" + lvIndex + "\t" + parameterNames);

                    if (pDef.getComment() != null) {
                        lines.add("\t\t\tc\t" + pDef.getComment());
                    }
                }

                for (final LocalVariableDef vDef : mDef.getLocalVariables()) {
                    final String variableNames = namespaces.stream().map(vDef::getName).collect(Collectors.joining("\t"));
                    final int lvIndex = vDef.getLocalVariableIndex();
                    final int lvStartOffset = vDef.getLocalVariableStartOffset();
                    final int optionalLvtIndex = vDef.getLocalVariableTableIndex();
                    lines.add("\t\tv\t" + lvIndex + "\t" + lvStartOffset + "\t" + optionalLvtIndex + "\t" + variableNames);

                    if (vDef.getComment() != null) {
                        lines.add("\t\t\tc\t" + vDef.getComment());
                    }
                }
            }
        }

        Files.write(targetPath, lines);
    }

    public static void main(final String... args) {
        new CommandLine(new Menufier()).execute(args);
    }
}
