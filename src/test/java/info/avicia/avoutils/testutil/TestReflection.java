package info.avicia.avoutils.testutil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Minimal reflection helpers for reading and writing private fields in tests.
 */
public final class TestReflection {

    private TestReflection() {
    }

    public static void set(Object target, String fieldName, Object value) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set field " + fieldName, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Object target, String fieldName) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to get field " + fieldName, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T invoke(Object target, String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return (T) method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke " + methodName, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T invokeStatic(Class<?> clazz, String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Method method = clazz.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return (T) method.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke static " + methodName, e);
        }
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
