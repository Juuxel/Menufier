/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package juuxel.menufier;

import net.fabricmc.mapping.reader.v2.TinyMetadata;
import net.fabricmc.mapping.tree.*;
import org.jetbrains.annotations.Nullable;
import org.organicdesign.fp.collections.ImList;
import org.organicdesign.fp.tuple.Tuple2;

import java.util.*;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.organicdesign.fp.StaticImports.*;

/**
 * A processor for Tiny mappings that replaces names based on regex patterns.
 */
public final class Replacer {
    private final String targetNamespace;
    private final Map<Pattern, UnaryOperator<String>> patterns;

    /**
     * Constructs a {@code Replacer}.
     *
     * @param targetNamespace the target namespace where the changes will be applied
     * @param patterns        the replacement patterns
     */
    public Replacer(final String targetNamespace, final Map<Pattern, UnaryOperator<String>> patterns) {
        this.targetNamespace = targetNamespace;
        this.patterns = patterns;
    }

    /**
     * Processes a single name with the replacement patterns.
     *
     * @param name the name to be processed
     * @return the name with the replacements applied
     */
    public String process(String name) {
        for (final Map.Entry<Pattern, UnaryOperator<String>> entry : patterns.entrySet()) {
            final Pattern pattern = entry.getKey();
            final Matcher matcher = pattern.matcher(name);

            if (matcher.matches()) {
                name = entry.getValue().apply(name);
            }
        }

        return name;
    }

    /**
     * Processes a {@code TinyTree} with the replacement patterns.
     *
     * @param input the input tiny tree
     * @return the output tiny tree
     * @throws IllegalArgumentException if the input tree does not contain the target namespace
     * @throws IllegalArgumentException if the target namespace is the default namespace of the input tree
     */
    public TinyTree process(final TinyTree input) {
        final List<String> namespaces = input.getMetadata().getNamespaces();

        if (!namespaces.contains(targetNamespace)) {
            throw new IllegalArgumentException("TinyTree does not contain namespace '" + targetNamespace + "'");
        }

        final String defaultNamespace = namespaces.get(0);

        if (targetNamespace.equals(defaultNamespace)) {
            throw new IllegalArgumentException("The target namespace must not be the default namespace of TinyTree");
        }

        final ImList<ClassDef> classes = xform(input.getClasses()).<ClassDef>map(cDef -> {
            final List<MethodDef> methods = xform(cDef.getMethods()).<MethodDef>map(mDef -> {
                final List<ParameterDef> parameters = xform(mDef.getParameters())
                        .<ParameterDef>map(pDef -> new MappedParameterDef(pDef, process(pDef.getName(targetNamespace))))
                        .toImList();

                final List<LocalVariableDef> localVariables = xform(mDef.getLocalVariables())
                        .<LocalVariableDef>map(lDef -> new MappedLocalVarDef(lDef, process(lDef.getName(targetNamespace))))
                        .toImList();

                return new MappedMethodDef(mDef, process(mDef.getName(targetNamespace)), parameters, localVariables);
            }).toImList();

            final List<FieldDef> fields = xform(cDef.getFields())
                    .<FieldDef>map(fDef -> new MappedFieldDef(fDef, process(fDef.getName(targetNamespace))))
                    .toImList();

            return new MappedClassDef(cDef, process(cDef.getName(targetNamespace)), methods, fields);
        }).toImList();

        final Map<String, ClassDef> byDefaultNamespace = classes.toImMap(def -> Tuple2.of(def.getName(defaultNamespace), def));

        return new TinyTree() {
            @Override
            public TinyMetadata getMetadata() {
                return input.getMetadata();
            }

            @Override
            public Map<String, ClassDef> getDefaultNamespaceClassMap() {
                return byDefaultNamespace;
            }

            @Override
            public Collection<ClassDef> getClasses() {
                return classes;
            }
        };
    }

    private abstract class MappedDef<D extends Mapped> implements Mapped {
        protected final D delegate;
        protected final String name;

        protected MappedDef(final D delegate, final String name) {
            this.delegate = delegate;
            this.name = name;
        }

        @Override
        public String getName(final String namespace) {
            if (namespace.equals(targetNamespace)) {
                return name;
            }

            return delegate.getName(namespace);
        }

        @Override
        public String getRawName(final String namespace) {
            if (namespace.equals(targetNamespace)) {
                return name;
            }

            return delegate.getRawName(namespace);
        }

        @Override
        public @Nullable String getComment() {
            return delegate.getComment();
        }
    }

    private final class MappedClassDef extends MappedDef<ClassDef> implements ClassDef {
        private final Collection<MethodDef> methods;
        private final Collection<FieldDef> fields;

        protected MappedClassDef(final ClassDef delegate, final String name, final Collection<MethodDef> methods, final Collection<FieldDef> fields) {
            super(delegate, name);
            this.methods = methods;
            this.fields = fields;
        }

        @Override
        public Collection<MethodDef> getMethods() {
            return methods;
        }

        @Override
        public Collection<FieldDef> getFields() {
            return fields;
        }
    }

    private abstract class MappedDescriptoredDef<D extends Descriptored> extends MappedDef<D> implements Descriptored {
        protected MappedDescriptoredDef(final D delegate, final String name) {
            super(delegate, name);
        }

        @Override
        public String getDescriptor(final String namespace) {
            return delegate.getDescriptor(namespace);
        }
    }

    private final class MappedMethodDef extends MappedDescriptoredDef<MethodDef> implements MethodDef {
        private final Collection<ParameterDef> parameters;
        private final Collection<LocalVariableDef> localVariables;

        protected MappedMethodDef(final MethodDef delegate, final String name, final Collection<ParameterDef> parameters, final Collection<LocalVariableDef> localVariables) {
            super(delegate, name);
            this.parameters = parameters;
            this.localVariables = localVariables;
        }

        @Override
        public Collection<ParameterDef> getParameters() {
            return parameters;
        }

        @Override
        public Collection<LocalVariableDef> getLocalVariables() {
            return localVariables;
        }
    }

    private final class MappedFieldDef extends MappedDescriptoredDef<FieldDef> implements FieldDef {
        protected MappedFieldDef(final FieldDef delegate, final String name) {
            super(delegate, name);
        }
    }

    private final class MappedParameterDef extends MappedDef<ParameterDef> implements ParameterDef {
        protected MappedParameterDef(final ParameterDef delegate, final String name) {
            super(delegate, name);
        }

        @Override
        public int getLocalVariableIndex() {
            return delegate.getLocalVariableIndex();
        }
    }

    private final class MappedLocalVarDef extends MappedDef<LocalVariableDef> implements LocalVariableDef {
        protected MappedLocalVarDef(final LocalVariableDef delegate, final String name) {
            super(delegate, name);
        }

        @Override
        public int getLocalVariableIndex() {
            return delegate.getLocalVariableIndex();
        }

        @Override
        public int getLocalVariableStartOffset() {
            return delegate.getLocalVariableStartOffset();
        }

        @Override
        public int getLocalVariableTableIndex() {
            return delegate.getLocalVariableTableIndex();
        }
    }
}
