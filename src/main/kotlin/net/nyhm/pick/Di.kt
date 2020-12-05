package net.nyhm.pick

import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

typealias DiFactory<T> = (di:Di) -> T

/**
 * Lazily create a singleton instance from the given factory.
 * The given factory is called at most once (only when requested).
 *
 * This is not thread safe. Concurrent invocations could cause the
 * given factory to be called more than once (and break the singleton contract).
 */
class SingletonDiFactory<T>(private val factory: DiFactory<T>): DiFactory<T> {
  private var instance: T? = null
  override fun invoke(di: Di): T {
    if (instance == null) instance = factory.invoke(di)
    return instance!!
  }
}

/**
 * Dependency injection instance provider. A Di instance should be obtained
 * from a [DiBuilder]. The builder provides [DiFactory] registration (ie,
 * application configuration), while the [Di] provides access to instances.
 *
 * When requesting an instance, if an exact match for the type is registered,
 * that factory is used to produce an instance. Otherwise, if a subclass of the
 * requested type is registered, that factory is used. If more than one subclass
 * is registered, one will be chosen (but which one is left undefined).
 */
class Di(
  private val map: Map<KClass<*>,DiFactory<*>>
) {
  /**
   * Get an instance of the requested type.
   * See class documentation for instance rules.
   */
  fun <T:Any> get(type: KClass<T>) = find(type).invoke(this)

  /**
   * Prettier form of `get(type)` allowing `di.get<Something>()` syntax.
   * The type can be omitted if the type can be inferred: `val a: Something = di.get()`
   *
   * Consider arguments to a constructor or function:
   * ```
   * class Something(foo: Foo, bar: Bar)
   * val s = Something(di.get(), di.get()) // types inferred
   * ```
   */
  inline fun <reified T:Any> get() = get(T::class) // find(T::class).invoke(this)

  /**
   * Get all instances matching the given (super) type.
   */
  fun <T:Any> getAll(type: KClass<T>): List<T> =
    map.entries.mapNotNull { (t, factory) ->
      @Suppress("UNCHECKED_CAST")
      if (t.isSubclassOf(type)) (factory as DiFactory<T>).invoke(this) else null
    }

  /**
   * Prettier form of `getAll(type)` allowing `di.get<Something>()` syntax.
   * The type can be omitted if the type can be inferred: `val a: List<Something> = di.getAll()`
   */
  inline fun <reified T:Any> getAll(): List<T> = getAll(T::class)

  /**
   * Utility method to locate the factory for a given type.
   * See class documentation for instance rules.
   */
  private fun <T:Any> find(type: KClass<T>): DiFactory<out T> {
    val factory = map[type] ?: map.entries.firstOrNull { it.key.isSubclassOf(type) }?.value ?: throw MissingTypeException(type)
    @Suppress("UNCHECKED_CAST") return factory as DiFactory<out T>
  }
}

// TODO: rename DiRegistrar?
/**
 * A dependency injection instance factory service.
 * - Use [DiBuilder] to register types with a factory for instances of that type.
 * - Use [Di] to retrieve object instances based on type (or supertype).
 *
 * A [Di] instance is bound to this builder. Changes to the registered factories
 * are reflected in the [Di] instance. The purpose of the builder is to separate
 * registration (configuration) from users that produce instances.
 */
class DiBuilder {
  private val map = mutableMapOf<KClass<*>,DiFactory<*>>()

  val di = Di(map)

  /**
   * Register a type with a factory that produces instances of that type.
   */
  fun <T:Any> register(type: KClass<T>, factory: DiFactory<out T>) = apply { map[type] = factory }

  /**
   * Prettier form of `register(type,factory)` allowing `di.register { Something(it.get()) }`
   * syntax if the type can be inferred.
   */
  inline fun <reified T:Any> register(noinline factory: DiFactory<out T>) = register(T::class, factory)

  /**
   * Register the runtime class of the given instance with the instance itself.
   */
  fun <T:Any> register(instance: T) = apply { map[instance::class] = { instance } }

  /**
   * Convenience method to register the specified type to a lazy singleton.
   */
  fun <T:Any> registerSingleton(type: KClass<T>, factory: DiFactory<out T>) = register(type, SingletonDiFactory(factory))

  /**
   * Prettier form of `registerSingleton(type,factory)` allowing `di.registerSingleton { Something() }`
   * syntax if the type can be inferred.
   */
  inline fun <reified T:Any> registerSingleton(noinline factory: DiFactory<out T>) = registerSingleton(T::class, factory)
}

sealed class DiException(msg: String): Exception(msg)
class MissingTypeException(val type: KClass<*>): DiException("No factory for $type")