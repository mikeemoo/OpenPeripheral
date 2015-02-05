package openperipheral.converter.outbound;

import java.util.List;
import java.util.Map;

import openperipheral.api.converter.IConverter;
import openperipheral.api.helpers.SimpleOutboundConverter;

import com.google.common.collect.Maps;

public class ConverterListOutbound extends SimpleOutboundConverter<List<?>> {

	@Override
	public Object convert(IConverter registry, List<?> list) {
		Map<Integer, Object> ret = Maps.newHashMap();

		for (int i = 0; i < list.size(); i++)
			ret.put(i + 1, registry.fromJava(list.get(i)));

		return ret;
	}

}