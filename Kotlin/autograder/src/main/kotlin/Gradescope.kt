package edu.berkeley.icsi.nweaver



import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher
import java.io.File


/**
 * This Kotlin file contains a set of functions used to enable
 * test functions to work well in the autograding infrastructure
 * on gradescope.
 *
 * The autograder's class should inherit off of GradescopeTest, and
 * the added GradescopeAnnotations allow annotating the individual
 * tests to provide scoring information.
 *
 * For robustness the test-runner will first try to run all tests with
 * a timeout of about 15 seconds, but if this fails it will go to its cached
 * set of tests and run each test individually.
 */


/**
 * This list is used to generate the JSON
 * output for autograding.
 */
private var resultList: MutableList<GradescopeTestJson> = mutableListOf()

/**
 * This is a parent class for the testing infrastructure that will collect up the
 * test results and dump them to a JSON file
 */
open class GradescopeTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            println("Setup Run $resultList")
            File("./build/reports/gradescope").mkdir()
        }

        private val json = Json { // this returns the JsonBuilder
            prettyPrint = true
        }

        @AfterAll
        @JvmStatic
        fun teardown(){
            var max = 0
            for (item in resultList){
                max += item.max_score
            }
            val resultListEncoded = json.encodeToString(resultList)
            println("Teardown Run $resultListEncoded")
            println("Maximum score $max")
            File("./build/reports/gradescope/testresults.json").writeText(resultListEncoded)
        }
    }
}

enum class Visibility {
    hidden, after_due_date, after_published, visible
}

@Target(AnnotationTarget.FUNCTION)
annotation class GradescopeAnnotation  (val name: String, val maxScore: Int,
                                        val visible : Visibility = Visibility.visible, val score: Int = 0) {
}

@Serializable
data class GradescopeTestJson (val name:String, val score: Int, val max_score: Int,
                               val visibility : Visibility, val output:String,
                               val javaName: String) {
}

/**
 * This class is used to override testSuccessful/testFailed
 * so we can snag the results and the gradescope annotation
 * and toss it in the test list.
 */
class GradescopeTestWatcher : TestWatcher {

    private val json = Json { // this returns the JsonBuilder
        prettyPrint = true
    }

    fun dumpJson(data : GradescopeTestJson) {
        val dataEncoded = json.encodeToString(data)
        File("./build/reports/gradescope/${data.javaName}.json").writeText(dataEncoded)
    }

    override fun testSuccessful(context: ExtensionContext?) {
        val annotations = context!!.requiredTestMethod.annotations
        val testClassName = context.requiredTestClass.name
        val testMethodName = context.requiredTestMethod.name
        val javaName = "$testClassName.$testMethodName"
        println("Successful test: $javaName")

        for (a in annotations){
            if (a is GradescopeAnnotation){
                val retval = GradescopeTestJson(a.name,
                    a.maxScore, a.maxScore, a.visible, "",
                    javaName)
                resultList.add(retval)
                dumpJson(retval)
            }
        }
        super.testSuccessful(context)
    }

    override fun testFailed(context: ExtensionContext?, cause: Throwable?) {

        val method = context!!.requiredTestMethod
        val annotations = method.annotations
        val testClassName = context.requiredTestClass.name
        val testMethodName = context.requiredTestMethod.name
        val javaName = "$testClassName.$testMethodName"

        println("Failed test: $testClassName $testMethodName")
        for (a in annotations){
            if (a is GradescopeAnnotation) {
                val retval = GradescopeTestJson(
                    a.name,
                    0, a.maxScore, a.visible, "Exception registered: $cause",
                    javaName)
                resultList.add(retval)
                dumpJson(retval)

            }
        }
        super.testFailed(context, cause)
    }
}