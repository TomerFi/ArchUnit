/*
 * Copyright 2014-2022 TNG Technology Consulting GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tngtech.archunit.core.importer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.importer.ImportedClasses.ImportedClassState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.tngtech.archunit.core.importer.ImportedClasses.ImportedClassState.HAD_TO_BE_IMPORTED;
import static java.lang.System.lineSeparator;

class DependencyResolutionProcess {
    private static final Logger log = LoggerFactory.getLogger(DependencyResolutionProcess.class);

    static final String DEPENDENCY_RESOLUTION_PROCESS_PROPERTY_PREFIX = "import.dependencyResolutionProcess";

    static final String MAX_ITERATIONS_FOR_MEMBER_TYPES_PROPERTY_NAME = "maxIterationsForMemberTypes";
    static final String MAX_ITERATIONS_FOR_ACCESSES_TO_TYPES_PROPERTY_NAME = "maxIterationsForAccessesToTypes";
    static final String MAX_ITERATIONS_FOR_SUPERTYPES_PROPERTY_NAME = "maxIterationsForSupertypes";
    static final String MAX_ITERATIONS_FOR_ENCLOSING_TYPES_PROPERTY_NAME = "maxIterationsForEnclosingTypes";
    static final String MAX_ITERATIONS_FOR_ANNOTATION_TYPES_PROPERTY_NAME = "maxIterationsForAnnotationTypes";
    static final String MAX_ITERATIONS_FOR_GENERIC_SIGNATURE_TYPES_PROPERTY_NAME = "maxIterationsForGenericSignatureTypes";

    static final int MAX_ITERATIONS_FOR_MEMBER_TYPES_DEFAULT_VALUE = 1;
    static final int MAX_ITERATIONS_FOR_ACCESSES_TO_TYPES_DEFAULT_VALUE = 1;
    static final int MAX_ITERATIONS_FOR_SUPERTYPES_DEFAULT_VALUE = -1;
    static final int MAX_ITERATIONS_FOR_ENCLOSING_TYPES_DEFAULT_VALUE = -1;
    static final int MAX_ITERATIONS_FOR_ANNOTATION_TYPES_DEFAULT_VALUE = -1;
    static final int MAX_ITERATIONS_FOR_GENERIC_SIGNATURE_TYPES_DEFAULT_VALUE = -1;

    private final ResolutionRun resolutionRun = new ResolutionRun();

    void registerMemberType(String typeName) {
        resolutionRun.registerMemberType(typeName);
    }

    public void registerMemberTypes(Collection<String> typeNames) {
        for (String typeName : typeNames) {
            registerMemberType(typeName);
        }
    }

    void registerAccessToType(String typeName) {
        resolutionRun.registerAccessToType(typeName);
    }

    public void registerSupertype(String typeName) {
        resolutionRun.registerSupertype(typeName);
    }

    public void registerSupertypes(Collection<String> typeNames) {
        for (String typeName : typeNames) {
            registerSupertype(typeName);
        }
    }

    public void registerEnclosingType(String typeName) {
        resolutionRun.registerEnclosingType(typeName);
    }

    public void registerAnnotationType(String typeName) {
        resolutionRun.registerAnnotationType(typeName);
    }

    public void registerGenericSignatureType(String typeName) {
        resolutionRun.registerGenericSignatureType(typeName);
    }

    public void resolve(ImportedClasses classes) {
        resolutionRun.execute(classes);
    }

    private static class ResolutionRun {
        private final Properties resolutionProcessProperties = ArchConfiguration.get().getSubProperties(DEPENDENCY_RESOLUTION_PROCESS_PROPERTY_PREFIX);

        private final int maxRunsForMemberTypes = getConfiguredIterations(
                MAX_ITERATIONS_FOR_MEMBER_TYPES_PROPERTY_NAME, MAX_ITERATIONS_FOR_MEMBER_TYPES_DEFAULT_VALUE);
        private final int maxRunsForAccessesToTypes = getConfiguredIterations(
                MAX_ITERATIONS_FOR_ACCESSES_TO_TYPES_PROPERTY_NAME, MAX_ITERATIONS_FOR_ACCESSES_TO_TYPES_DEFAULT_VALUE);
        private final int maxRunsForSupertypes = getConfiguredIterations(
                MAX_ITERATIONS_FOR_SUPERTYPES_PROPERTY_NAME, MAX_ITERATIONS_FOR_SUPERTYPES_DEFAULT_VALUE);
        private final int maxRunsForEnclosingTypes = getConfiguredIterations(
                MAX_ITERATIONS_FOR_ENCLOSING_TYPES_PROPERTY_NAME, MAX_ITERATIONS_FOR_ENCLOSING_TYPES_DEFAULT_VALUE);
        private final int maxRunsForAnnotationTypes = getConfiguredIterations(
                MAX_ITERATIONS_FOR_ANNOTATION_TYPES_PROPERTY_NAME, MAX_ITERATIONS_FOR_ANNOTATION_TYPES_DEFAULT_VALUE);
        private final int maxRunsForGenericSignatureTypes = getConfiguredIterations(
                MAX_ITERATIONS_FOR_GENERIC_SIGNATURE_TYPES_PROPERTY_NAME, MAX_ITERATIONS_FOR_GENERIC_SIGNATURE_TYPES_DEFAULT_VALUE);

        private int getConfiguredIterations(String propertyName, int defaultValue) {
            return Integer.parseInt(resolutionProcessProperties.getProperty(propertyName, String.valueOf(defaultValue)));
        }

        private Set<String> currentTypeNames = new HashSet<>();

        private int runNumber = 1;
        private boolean shouldContinue;

        void registerMemberType(String typeName) {
            if (runNumberHasNotExceeded(maxRunsForMemberTypes)) {
                currentTypeNames.add(typeName);
            }
        }

        void registerAccessToType(String typeName) {
            if (runNumberHasNotExceeded(maxRunsForAccessesToTypes)) {
                currentTypeNames.add(typeName);
            }
        }

        void registerSupertype(String typeName) {
            if (runNumberHasNotExceeded(maxRunsForSupertypes)) {
                currentTypeNames.add(typeName);
            }
        }

        void registerEnclosingType(String typeName) {
            if (runNumberHasNotExceeded(maxRunsForEnclosingTypes)) {
                currentTypeNames.add(typeName);
            }
        }

        void registerAnnotationType(String typeName) {
            if (runNumberHasNotExceeded(maxRunsForAnnotationTypes)) {
                currentTypeNames.add(typeName);
            }
        }

        public void registerGenericSignatureType(String typeName) {
            if (runNumberHasNotExceeded(maxRunsForGenericSignatureTypes)) {
                currentTypeNames.add(typeName);
            }
        }

        private boolean runNumberHasNotExceeded(int maxRuns) {
            return maxRuns < 0 || runNumber <= maxRuns;
        }

        void execute(ImportedClasses classes) {
            logConfiguration();
            do {
                executeRun(classes);
            } while (shouldContinue);
        }

        private void logConfiguration() {
            log.info("Automatically resolving transitive class dependencies with the following configuration:{}{}{}{}{}{}",
                    formatConfigProperty(MAX_ITERATIONS_FOR_MEMBER_TYPES_PROPERTY_NAME, maxRunsForMemberTypes),
                    formatConfigProperty(MAX_ITERATIONS_FOR_ACCESSES_TO_TYPES_PROPERTY_NAME, maxRunsForAccessesToTypes),
                    formatConfigProperty(MAX_ITERATIONS_FOR_SUPERTYPES_PROPERTY_NAME, maxRunsForSupertypes),
                    formatConfigProperty(MAX_ITERATIONS_FOR_ENCLOSING_TYPES_PROPERTY_NAME, maxRunsForEnclosingTypes),
                    formatConfigProperty(MAX_ITERATIONS_FOR_ANNOTATION_TYPES_PROPERTY_NAME, maxRunsForAnnotationTypes),
                    formatConfigProperty(MAX_ITERATIONS_FOR_GENERIC_SIGNATURE_TYPES_PROPERTY_NAME, maxRunsForGenericSignatureTypes));
        }

        private String formatConfigProperty(String propertyName, int number) {
            return lineSeparator() + DEPENDENCY_RESOLUTION_PROCESS_PROPERTY_PREFIX + "." + propertyName + " = " + number;
        }

        private void executeRun(ImportedClasses classes) {
            runNumber++;
            Set<String> typeNamesToResolve = this.currentTypeNames;
            currentTypeNames = new HashSet<>();
            shouldContinue = false;
            for (String typeName : typeNamesToResolve) {
                ImportedClassState classState = classes.ensurePresent(typeName);
                shouldContinue = shouldContinue || (classState == HAD_TO_BE_IMPORTED);
            }
        }
    }
}
