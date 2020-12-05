package net.nyhm.pick

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.full.isSubclassOf

interface Fruit

/** A certain [kind] of Apple */
data class Apple(val kind: String): Fruit

interface Tropical: Fruit

object Banana: Tropical
object Passion: Tropical

interface Citrus: Fruit

class Orange(val peeler: Peeler): Citrus
class Lemon: Citrus

interface Peeler
class ManualPeeler: Peeler
class AutoPeeler: Peeler

sealed class Vegetable(val slicer: Slicer)
class Carrot(val peeler: Peeler, slicer: Slicer): Vegetable(slicer)

interface Slicer
object ManualSlicer: Slicer
object AutoSlicer: Slicer

class InstanceCounter {
  val id: Int = ++instanceCount
  companion object {
    var instanceCount = 0
  }
}

internal class DiTest {
  @Test
  fun testDi() {
    val builder = DiBuilder()
    val di = builder.di

    builder.register(Banana::class) { Banana }
    builder.register(Lemon::class) { Lemon() }
    assertTrue(di.get(Fruit::class)::class.isSubclassOf(Fruit::class))
    assertTrue(di.get(Banana::class)::class == Banana::class)

    builder.register(Orange::class) { Orange(it.get(Peeler::class)) }
    builder.register(Peeler::class) { ManualPeeler() }
    val orange = di.get(Orange::class)
    assertTrue(orange::class == Orange::class)
    assertTrue(orange.peeler is ManualPeeler)

    builder.register(Vegetable::class) { Carrot(it.get(), it.get()) } // inferred types
    try {
      di.get<Vegetable>() // missing slicer
    } catch (e: MissingTypeException) {
      assertTrue(e.type == Slicer::class)
    }

    builder.register(ManualSlicer)
    val veg = di.get<Vegetable>()
    assertTrue(veg.slicer is ManualSlicer)
    assertTrue(veg is Carrot && veg.peeler is ManualPeeler)

    val allFruit: List<Fruit> = di.getAll()
    assertEquals(
      allFruit.map { it::class },
      di.getAll<Fruit>().map { it::class }) // same classes, maybe not same instances
    assertEquals(allFruit.map { it::class }, di.getAll(Fruit::class).map { it::class })
    assertTrue(allFruit.map { it::class }.containsAll(listOf(Banana::class, Orange::class, Lemon::class)))
  }

  @Test
  fun testInstances() {
    val builder = DiBuilder()
    val di = builder.di

    assertEquals(0, InstanceCounter.instanceCount)
    val first = InstanceCounter()
    assertEquals(1, InstanceCounter.instanceCount)
    assertEquals(1, first.id)

    builder.register(InstanceCounter())
    assertEquals(2, InstanceCounter.instanceCount)
    assertEquals(2, di.get<InstanceCounter>().id)
    assertEquals(2, di.get<InstanceCounter>().id)

    builder.register(InstanceCounter::class) { InstanceCounter() }
    assertEquals(3, di.get<InstanceCounter>().id)
    assertEquals(4, di.get<InstanceCounter>().id)
    assertEquals(5, di.get<InstanceCounter>().id)

    builder.registerSingleton(InstanceCounter::class) { InstanceCounter() }
    assertEquals(5, InstanceCounter.instanceCount) // no new instances yet
    assertEquals(6, di.get<InstanceCounter>().id) // instance created
    assertEquals(6, di.get<InstanceCounter>().id) // same instance
    assertEquals(6, InstanceCounter.instanceCount) // for good measure
  }
}