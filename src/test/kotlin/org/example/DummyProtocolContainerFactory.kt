package org.example

class DummyProtocolContainerFactory : ProtocolContainerFactory {
    override val protocols = listOf("dummytest")

    override fun create(protocol: String, route: RouteConfig): Container {
        return HelloDummyBackedContainer(SystemClock())
    }
}
