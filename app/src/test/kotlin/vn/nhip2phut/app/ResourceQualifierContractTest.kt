package vn.nhip2phut.app

import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResourceQualifierContractTest {
    @Test
    fun `app and ui exact vi-VN string keys match their defaults`() {
        val repositoryRoot = locateRepositoryRoot()

        assertStringKeyParity(repositoryRoot.resolve("app/src/main/res"))
        assertStringKeyParity(repositoryRoot.resolve("ui/src/main/res"))
    }

    private fun assertStringKeyParity(resourceRoot: Path) {
        val defaultStrings = resourceRoot.resolve("values/strings.xml")
        val exactViVnStrings = resourceRoot.resolve("values-b+vi+VN/strings.xml")

        assertTrue(
            actual = Files.isRegularFile(defaultStrings),
            message = "Missing default strings: $defaultStrings",
        )
        assertTrue(
            actual = Files.isRegularFile(exactViVnStrings),
            message = "Missing exact vi-VN strings: $exactViVnStrings",
        )
        assertEquals(readStringKeys(defaultStrings), readStringKeys(exactViVnStrings))
    }

    private fun readStringKeys(path: Path): Set<String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(path.toFile())
        val strings = document.getElementsByTagName("string")

        return buildSet {
            repeat(strings.length) { index ->
                add(strings.item(index).attributes.getNamedItem("name").nodeValue)
            }
        }
    }

    private fun locateRepositoryRoot(): Path {
        val current = Path.of("").toAbsolutePath().normalize()
        return when {
            Files.isRegularFile(current.resolve("settings.gradle.kts")) -> current
            Files.isRegularFile(current.parent.resolve("settings.gradle.kts")) -> current.parent
            else -> error("Cannot locate repository root from $current")
        }
    }
}
