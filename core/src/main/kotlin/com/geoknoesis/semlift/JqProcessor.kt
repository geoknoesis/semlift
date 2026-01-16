package io.semlift

class JqProcessor(private val jqBinary: String) : JqRunner {
    override fun apply(program: String, input: ByteArray): ByteArray {
        val process = ProcessBuilder(jqBinary, program)
            .redirectErrorStream(false)
            .start()

        process.outputStream.use { output ->
            output.write(input)
        }

        val stdout = process.inputStream.readBytes()
        val stderr = process.errorStream.readBytes()
        val exit = process.waitFor()
        if (exit != 0) {
            val errorText = stderr.toString(Charsets.UTF_8)
            throw IllegalStateException("jq failed with exit code $exit: $errorText")
        }
        return stdout
    }
}

