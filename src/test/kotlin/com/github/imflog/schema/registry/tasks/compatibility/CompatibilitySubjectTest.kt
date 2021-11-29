package com.github.imflog.schema.registry.tasks.compatibility

import com.github.imflog.schema.registry.MixedReferenceException
import com.github.imflog.schema.registry.SchemaType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CompatibilitySubjectTest {

    @Test
    fun `Should not allow mixed references`() {
        assertThrows<MixedReferenceException> {
            CompatibilitySubject("test", "test", SchemaType.AVRO)
                .addReference("remote_ref", "remote_subject", -1)
                .addLocalReference("local_ref", "local_path")
        }
    }
}
