package openperipheral.converter;

import com.google.common.reflect.TypeToken;
import java.lang.reflect.Type;
import openperipheral.api.converter.IConverter;
import openperipheral.api.converter.IGenericInboundTypeConverter;

public abstract class GenericInboundConverterAdapter implements IGenericInboundTypeConverter {

	@Override
	public Object toJava(IConverter registry, Object obj, Type expected) {
		TypeToken<?> type = TypeToken.of(expected);
		return toJava(registry, obj, type.getRawType());
	}

	protected abstract Object toJava(IConverter converter, Object obj, Class<?> expected);
}
