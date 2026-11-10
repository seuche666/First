package de.fernfahrer.fahrtenbuch

import de.fernfahrer.fahrtenbuch.ui.editor.EditorState
import de.fernfahrer.fahrtenbuch.ui.editor.EditorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorRulesTest {
    @Test
    fun automatischesArbeitsendeNurNachArbeitsbeginn() {
        assertEquals(550, EditorViewModel.autoWorkEnd(workStart = 540, end = 550))
        assertEquals(550, EditorViewModel.autoWorkEnd(workStart = null, end = 550))
        // "Arbeit beginnen" um 09:12, Tour wird kurz danach mit 09:10 beendet → kein falsches Ende
        assertNull(EditorViewModel.autoWorkEnd(workStart = 552, end = 550))
    }

    @Test
    fun arbeitsendeVorArbeitsbeginnWirdAbgelehnt() {
        val base = EditorState(loading = false, startMinute = 0, endMinute = 1440)
        assertNull(EditorViewModel.validate(base.copy(workStart = 360, workEnd = 1080)))
        assertNull(EditorViewModel.validate(base.copy(workStart = 360)))
        assertEquals(
            "Das Arbeitsende muss nach dem Arbeitsbeginn liegen.",
            EditorViewModel.validate(base.copy(workStart = 600, workEnd = 540)),
        )
    }
}
