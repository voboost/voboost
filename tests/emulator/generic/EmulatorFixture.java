package ru.voboost.voyemu;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/** Supplies a session already started by the emulator lifecycle. */
public final class EmulatorFixture implements ParameterResolver, BeforeEachCallback {
    private record Inputs(EmulatorPreparation session, EmulatorPreparation.Candidate previous) {}

    private static final ThreadLocal<Inputs> INPUTS = new ThreadLocal<>();

    static void bind(EmulatorPreparation session, EmulatorPreparation.Candidate previous) {
        INPUTS.set(new Inputs(session, previous));
    }

    static void unbind() {
        INPUTS.remove();
    }

    @Override public void beforeEach(ExtensionContext context) throws Exception {
        Inputs inputs = INPUTS.get();
        if (inputs != null) inputs.session().run.requireTestReady();
    }

    @Override public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        Class<?> type = parameter.getParameter().getType();
        return type == EmulatorPreparation.class || type == EmulatorPreparation.Candidate.class;
    }

    @Override public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        Inputs inputs = INPUTS.get();
        if (inputs == null) return null;
        return parameter.getParameter().getType() == EmulatorPreparation.class ? inputs.session() : inputs.previous();
    }
}
