package openperipheral.adapter.property;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.List;

import openperipheral.adapter.IMethodDescription;
import openperipheral.adapter.IMethodExecutor;
import openperipheral.adapter.types.IType;
import openperipheral.adapter.types.TypeHelper;
import openperipheral.api.adapter.*;
import openperipheral.api.adapter.IndexedCallbackProperty.GetFromFieldType;
import openperipheral.api.adapter.method.ArgType;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class PropertyListBuilder {

	private class Parameters {
		public final String name;
		public final String getterDescription;
		public final String setterDescription;
		public final boolean isDelegating;
		public final boolean readOnly;
		public final boolean valueNullable;

		public Parameters(String name, String getterDescription, String setterDescription, boolean isDelegating, boolean readOnly, boolean valueNullable) {
			this.name = Strings.isNullOrEmpty(name)? field.getName() : name;
			this.getterDescription = getterDescription;
			this.setterDescription = setterDescription;
			this.isDelegating = isDelegating;
			this.readOnly = readOnly;
			this.valueNullable = valueNullable;
		}
	}

	private class SingleParameters extends Parameters {

		public final IType valueType;

		public SingleParameters(String name, String getterDescription, String setterDescription, boolean isDelegating, boolean readOnly, boolean valueNullable, ArgType valueType) {
			super(name, getterDescription, setterDescription, isDelegating, readOnly, valueNullable);
			this.valueType = TypeHelper.interpretArgType(valueType, field.getGenericType());
		}
	}

	private class IndexedParameters extends Parameters {
		public final boolean expandable;
		public final Type keyType;
		public final IType docKeyType;
		public final IValueTypeProvider valueTypeProvider;
		public final IType docValueType;

		public IndexedParameters(String name, String getterDescription, String setterDescription, boolean isDelegating, boolean readOnly, boolean valueNullable, boolean expandable, Class<?> keyType, ArgType keyDocType, Class<?> valueType, ArgType valueDocType) {
			super(name, getterDescription, setterDescription, isDelegating, readOnly, valueNullable);
			this.expandable = expandable;

			final FieldTypeInfoBuilder fieldTypeBuilder = new FieldTypeInfoBuilder(field.getGenericType());

			if (keyType != GetFromFieldType.class) fieldTypeBuilder.overrideKeyType(keyType);
			if (keyDocType != ArgType.AUTO) fieldTypeBuilder.overrideKeyDocType(TypeHelper.single(keyDocType));

			if (valueType != GetFromFieldType.class) fieldTypeBuilder.overrideValueType(valueType);
			if (valueDocType != ArgType.AUTO) fieldTypeBuilder.overrideValueDocType(TypeHelper.single(valueDocType));

			final FieldTypeInfoBuilder.Result types = fieldTypeBuilder.build();

			this.keyType = types.keyType;
			this.docKeyType = types.keyDocType;

			this.valueTypeProvider = types.valueType;
			this.docValueType = types.valueDocType;
		}
	}

	private final Field field;
	private final String source;
	private SingleParameters singleParameters;
	private IndexedParameters indexedParameters;

	public PropertyListBuilder(Field field, String source) {
		this.field = field;
		this.source = source;
	}

	public void addSingle(String name, String getterDescription, String setterDescription, boolean isDelegating, boolean readOnly, boolean valueNullable, ArgType type) {
		this.singleParameters = new SingleParameters(name, getterDescription, setterDescription, isDelegating, readOnly, valueNullable, type);
	}

	public void addProperty(Property property) {
		addSingle(property.name(), property.getterDesc(), property.setterDesc(), false, property.readOnly(), property.nullable(), property.type());
	}

	public void addProperty(CallbackProperty property) {
		Preconditions.checkArgument(IPropertyCallback.class.isAssignableFrom(field.getDeclaringClass()));
		addSingle(property.name(), property.getterDesc(), property.setterDesc(), true, property.readOnly(), property.nullable(), property.type());
	}

	public void addIndexed(String name, String getterDescription, String setterDescription, boolean isDelegating, boolean readOnly, boolean valueNullable, boolean expandable, Class<?> keyType, ArgType keyDocType, Class<?> valueType, ArgType valueDocType) {
		this.indexedParameters = new IndexedParameters(name, getterDescription, setterDescription, isDelegating, readOnly, valueNullable, expandable, keyType, keyDocType, valueType, valueDocType);
	}

	public void addProperty(IndexedProperty property) {
		addIndexed(property.name(), property.getterDesc(), property.setterDesc(), false, property.readOnly(), property.nullable(), property.expandable(), GetFromFieldType.class, property.keyType(), GetFromFieldType.class, ArgType.AUTO);
	}

	public void addProperty(IndexedCallbackProperty property) {
		Preconditions.checkArgument(IIndexedPropertyCallback.class.isAssignableFrom(field.getDeclaringClass()));
		addIndexed(property.name(), property.getterDesc(), property.setterDesc(), true, property.readOnly(), property.nullable(), false, property.keyType(), property.keyDocType(), property.valueType(), property.valueDocType());
	}

	public PropertyListBuilder configureFromFieldProperties() {
		final Property singleProperty = field.getAnnotation(Property.class);
		final CallbackProperty singleCallbackProperty = field.getAnnotation(CallbackProperty.class);
		if (singleProperty != null) addProperty(singleProperty);
		else if (singleCallbackProperty != null) addProperty(singleCallbackProperty);

		final IndexedProperty indexedProperty = field.getAnnotation(IndexedProperty.class);
		final IndexedCallbackProperty indexedCallbackProperty = field.getAnnotation(IndexedCallbackProperty.class);
		if (indexedProperty != null) addProperty(indexedProperty);
		else if (indexedCallbackProperty != null) addProperty(indexedCallbackProperty);

		return this;
	}

	public void addMethods(List<IMethodExecutor> output) {
		field.setAccessible(true);

		if (singleParameters != null && indexedParameters == null) {
			addSinglePropertyMethods(output, singleParameters);
		} else if (singleParameters == null && indexedParameters != null) {
			addIndexedPropertyMethods(output, indexedParameters);
		} else if (singleParameters != null && indexedParameters != null) {
			if (singleParameters.name.equals(indexedParameters.name)) {
				addMergedPropertyMethods(output, singleParameters, indexedParameters);
			} else {
				addSinglePropertyMethods(output, singleParameters);
				addIndexedPropertyMethods(output, indexedParameters);
			}
		}
	}

	private void addSinglePropertyMethods(List<IMethodExecutor> output, SingleParameters params) {
		precheckSingleField(params);
		final IFieldManipulator fieldManipulator = SingleManipulatorProvider.getProvider(params.isDelegating);
		output.add(createSinglePropertyGetter(params, fieldManipulator));
		if (!params.readOnly) output.add(createSinglePropertySetter(params, fieldManipulator));
	}

	private void addIndexedPropertyMethods(List<IMethodExecutor> output, IndexedParameters params) {
		precheckIndexedField(params);
		final IIndexedFieldManipulator fieldManipulator = IndexedManipulatorProvider.getProvider(field.getType(), params.isDelegating, params.expandable);
		output.add(createIndexedPropertyGetter(params, fieldManipulator));
		if (!params.readOnly) output.add(createIndexedPropertySetter(params, fieldManipulator));
	}

	private void addMergedPropertyMethods(List<IMethodExecutor> output, SingleParameters singleParameters, IndexedParameters indexedParameters) {
		precheckSingleField(singleParameters);
		precheckIndexedField(indexedParameters);

		final IFieldManipulator singleFieldManipulator = SingleManipulatorProvider.getProvider(singleParameters.isDelegating);
		final IIndexedFieldManipulator indexedFieldManipulator = IndexedManipulatorProvider.getProvider(field.getType(), indexedParameters.isDelegating, indexedParameters.expandable);

		output.add(createMergedPropertyGetter(singleParameters, singleFieldManipulator, indexedParameters, indexedFieldManipulator));

		if (!singleParameters.readOnly && !indexedParameters.readOnly) {
			output.add(createMergedPropertySetter(singleParameters, singleFieldManipulator, indexedParameters, indexedFieldManipulator));
		} else if (!indexedParameters.readOnly) {
			output.add(createIndexedPropertySetter(indexedParameters, indexedFieldManipulator));
		} else if (!singleParameters.readOnly) {
			output.add(createSinglePropertySetter(singleParameters, singleFieldManipulator));
		}
	}

	private IMethodExecutor createSinglePropertyGetter(SingleParameters params, final IFieldManipulator fieldManipulator) {
		final PropertyDescriptionBuilder descriptionBuilder = new PropertyDescriptionBuilder(params.name, source);
		descriptionBuilder.addSingleParameter(params.valueType);
		if (!Strings.isNullOrEmpty(params.getterDescription)) descriptionBuilder.overrideDescription(params.getterDescription);
		final IMethodDescription description = descriptionBuilder.buildGetter();
		final IPropertyExecutor caller = new GetterExecutor(field, fieldManipulator);
		return new PropertyExecutor(description, caller);
	}

	private IMethodExecutor createSinglePropertySetter(SingleParameters params, final IFieldManipulator fieldManipulator) {
		final PropertyDescriptionBuilder descriptionBuilder = new PropertyDescriptionBuilder(params.name, source);
		descriptionBuilder.addSingleParameter(params.valueType);
		if (!Strings.isNullOrEmpty(params.setterDescription)) descriptionBuilder.overrideDescription(params.setterDescription);
		final IMethodDescription description = descriptionBuilder.buildSetter();
		final IPropertyExecutor caller = new SetterExecutor(field, fieldManipulator, params.valueNullable);
		return new PropertyExecutor(description, caller);
	}

	private IMethodExecutor createIndexedPropertyGetter(IndexedParameters params, final IIndexedFieldManipulator fieldManipulator) {
		final PropertyDescriptionBuilder descriptionBuilder = new PropertyDescriptionBuilder(params.name, source);
		descriptionBuilder.addIndexParameter(params.docKeyType, params.docValueType);

		if (!Strings.isNullOrEmpty(params.getterDescription)) descriptionBuilder.overrideDescription(params.getterDescription);

		final IMethodDescription description = descriptionBuilder.buildGetter();
		final IPropertyExecutor caller = new IndexedGetterExecutor(field, fieldManipulator, params.keyType);
		return new PropertyExecutor(description, caller);
	}

	private IMethodExecutor createIndexedPropertySetter(IndexedParameters params, final IIndexedFieldManipulator fieldManipulator) {
		final PropertyDescriptionBuilder descriptionBuilder = new PropertyDescriptionBuilder(params.name, source);
		descriptionBuilder.addIndexParameter(params.docKeyType, params.docValueType);

		if (!Strings.isNullOrEmpty(params.setterDescription)) descriptionBuilder.overrideDescription(params.setterDescription);

		final IMethodDescription description = descriptionBuilder.buildSetter();
		final IPropertyExecutor caller = new IndexedSetterExecutor(field, fieldManipulator, params.keyType, params.valueTypeProvider, params.valueNullable);
		return new PropertyExecutor(description, caller);
	}

	private IMethodExecutor createMergedPropertyGetter(SingleParameters singleParameters, IFieldManipulator singleFieldManipulator, IndexedParameters indexedParameters, IIndexedFieldManipulator indexedFieldManipulator) {
		final PropertyDescriptionBuilder descriptionBuilder = new PropertyDescriptionBuilder(singleParameters.name, source);
		descriptionBuilder.addSingleParameter(singleParameters.valueType);
		descriptionBuilder.addIndexParameter(indexedParameters.docKeyType, indexedParameters.docValueType);

		if (!Strings.isNullOrEmpty(singleParameters.getterDescription)) descriptionBuilder.overrideDescription(singleParameters.getterDescription);
		else if (!Strings.isNullOrEmpty(indexedParameters.getterDescription)) descriptionBuilder.overrideDescription(indexedParameters.getterDescription);

		final IMethodDescription description = descriptionBuilder.buildGetter();
		final IPropertyExecutor caller = new MergedGetterExecutor(field, singleFieldManipulator, indexedFieldManipulator, indexedParameters.keyType);
		return new PropertyExecutor(description, caller);
	}

	private IMethodExecutor createMergedPropertySetter(SingleParameters singleParameters, IFieldManipulator singleFieldManipulator, IndexedParameters indexedParameters, IIndexedFieldManipulator indexedFieldManipulator) {
		final PropertyDescriptionBuilder descriptionBuilder = new PropertyDescriptionBuilder(singleParameters.name, source);
		descriptionBuilder.addSingleParameter(singleParameters.valueType);
		descriptionBuilder.addIndexParameter(indexedParameters.docKeyType, indexedParameters.docValueType);
		if (!Strings.isNullOrEmpty(singleParameters.setterDescription)) descriptionBuilder.overrideDescription(singleParameters.setterDescription);
		else if (!Strings.isNullOrEmpty(singleParameters.setterDescription)) descriptionBuilder.overrideDescription(singleParameters.setterDescription);
		final IMethodDescription description = descriptionBuilder.buildSetter();
		final IPropertyExecutor caller = new MergedSetterExecutor(field, singleParameters.valueNullable, singleFieldManipulator, indexedParameters.valueNullable, indexedFieldManipulator, indexedParameters.keyType, indexedParameters.valueTypeProvider);
		return new PropertyExecutor(description, caller);
	}

	private void precheckSingleField(SingleParameters params) {
		final int modifiers = field.getModifiers();
		Preconditions.checkArgument(!Modifier.isStatic(modifiers), "Field marked with @Property can't be static");
		Preconditions.checkArgument(params.readOnly || !Modifier.isFinal(modifiers), "Only fields marked with @Property(readOnly = true) can be marked final");
		Preconditions.checkArgument(!(params.valueNullable && field.getType().isPrimitive()), "Fields with primitive types can't be nullable");
	}

	private void precheckIndexedField(IndexedParameters params) {
		final int modifiers = field.getModifiers();
		Preconditions.checkArgument(!Modifier.isStatic(modifiers), "Field marked with @IndexedProperty can't be static");
		Preconditions.checkArgument(!params.expandable || !params.readOnly, "@IndexedProperty fields can't be both read-only and expandable");
		Preconditions.checkArgument(!params.expandable || !Modifier.isFinal(modifiers), "Only non-final @IndexedProperty fields can me expandable");
	}

	public static void buildPropertyList(Class<?> targetCls, String source, List<IMethodExecutor> output) {
		for (Field f : targetCls.getDeclaredFields())
			new PropertyListBuilder(f, source).configureFromFieldProperties().addMethods(output);
	}

}