package openperipheral.interfaces.oc;

import li.cil.oc.api.Driver;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Value;
import openperipheral.ApiProvider;
import openperipheral.adapter.AdapterRegistry;
import openperipheral.adapter.composed.ComposedMethodsFactory;
import openperipheral.adapter.composed.MethodSelector;
import openperipheral.adapter.method.LuaTypeQualifier;
import openperipheral.api.Constants;
import openperipheral.api.adapter.method.ArgType;
import openperipheral.api.architecture.IArchitectureAccess;
import openperipheral.api.converter.IConverter;
import openperipheral.converter.TypeConvertersProvider;
import openperipheral.interfaces.oc.providers.AdapterFactoryWrapperOC;
import openperipheral.interfaces.oc.providers.DriverOpenPeripheral;

public class ModuleOpenComputers {

	public static final ComposedMethodsFactory OBJECT_METHODS_FACTORY;

	public static final ComposedMethodsFactory PERIPHERAL_METHODS_FACTORY;

	static {
		final MethodSelector peripheralSelector = new MethodSelector(Constants.ARCH_OPEN_COMPUTERS)
				.addDefaultEnv()
				.addProvidedEnv(Constants.ARG_ACCESS, IArchitectureAccess.class)
				.addProvidedEnv(Constants.ARG_CONTEXT, Context.class);

		PERIPHERAL_METHODS_FACTORY = new ComposedMethodsFactory(AdapterRegistry.PERIPHERAL_ADAPTERS, peripheralSelector);

		final MethodSelector objectSelector = new MethodSelector(Constants.ARCH_OPEN_COMPUTERS)
				.addDefaultEnv()
				.addProvidedEnv(Constants.ARG_CONTEXT, Context.class);

		OBJECT_METHODS_FACTORY = new ComposedMethodsFactory(AdapterRegistry.OBJECT_ADAPTERS, objectSelector);
	}

	public static void init() {
		IConverter converter = new TypeConversionRegistryOC();
		TypeConvertersProvider.INSTANCE.registerConverter(Constants.ARCH_OPEN_COMPUTERS, converter);

		LuaTypeQualifier.registerType(Value.class, ArgType.OBJECT);
	}

	public static void registerProvider() {
		Driver.add(new DriverOpenPeripheral());
	}

	public static void installAPI(ApiProvider apiProvider) {
		apiProvider.registerClass(AdapterFactoryWrapperOC.class);
	}
}
