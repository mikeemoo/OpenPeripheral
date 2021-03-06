package openperipheral.adapter.composed;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import openperipheral.Config;
import openperipheral.adapter.AdapterRegistry;
import openperipheral.adapter.IMethodExecutor;

public class ClassMethodsComposer {

	private final Predicate<IMethodExecutor> selector;

	public ClassMethodsComposer(Predicate<IMethodExecutor> selector) {
		this.selector = selector;
	}

	public Map<String, IMethodExecutor> createMethodsList(Class<?> cls, AdapterRegistry manager) {
		ClassMethodsListBuilder builder = new ClassMethodsListBuilder(manager, selector);
		final List<Class<?>> classHierarchy = Lists.reverse(listSuperClasses(cls));

		Set<Class<?>> allSuperInterfaces = Sets.newHashSet();
		for (Class<?> c : classHierarchy)
			allSuperInterfaces.addAll(listSuperInterfaces(c));

		for (Class<?> c : allSuperInterfaces) {
			builder.addExternalAdapters(cls, c);
			builder.addInlineAdapter(cls, c);
		}

		for (Class<?> c : classHierarchy) {
			builder.addExternalAdapters(cls, c);
			builder.addInlineAdapter(cls, c);
		}

		if (Config.devMethods) builder.addMethodsFromObject(new LuaReflectionHelper(), cls, "<reflection>");

		if (!builder.hasMethods()) return ImmutableMap.of();

		builder.addMethodsFromObject(new MethodsListerHelper(builder.getMethodList(), builder.getSources()), cls, "<meta>");

		return builder.create();
	}

	private static List<Class<?>> listSuperClasses(Class<?> cls) {
		List<Class<?>> superClasses = Lists.newArrayList();
		Class<?> currentClass = cls;
		while (currentClass != Object.class) {
			superClasses.add(currentClass);
			currentClass = currentClass.getSuperclass();
		}
		return superClasses;
	}

	private static Set<Class<?>> listSuperInterfaces(Class<?> cls) {
		Set<Class<?>> superInterfaces = Sets.newHashSet();
		Queue<Class<?>> tbd = Lists.newLinkedList();
		tbd.addAll(Arrays.asList(cls.getInterfaces()));

		Class<?> currentClass;
		while ((currentClass = tbd.poll()) != null) {
			superInterfaces.add(currentClass);
			tbd.addAll(Arrays.asList(currentClass.getInterfaces()));
		}
		return superInterfaces;
	}
}
