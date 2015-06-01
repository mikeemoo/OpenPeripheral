package openperipheral.interfaces.cc;

import openperipheral.converter.TypeConverter;
import dan200.computercraft.api.lua.ILuaObject;

public class TypeConversionRegistryCC extends TypeConverter {

	private static final int LUA_OFFSET = 1;

	public TypeConversionRegistryCC() {
		super(LUA_OFFSET);
		registerIgnored(ILuaObject.class, true);
		outbound.addFirst(new ConverterCallableCC());
	}

}
