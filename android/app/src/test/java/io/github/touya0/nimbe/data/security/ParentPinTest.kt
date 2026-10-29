package io.github.touya0.nimbe.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentPinTest {

    @Test
    fun `seuls quatre chiffres sont un PIN`() {
        assertTrue(PinHash.isValid("0427"))
        assertFalse(PinHash.isValid("123"))
        assertFalse(PinHash.isValid("12345"))
        assertFalse(PinHash.isValid("12a4"))
    }

    @Test
    fun `l'empreinte reconnaît le bon PIN et lui seul`() {
        val stored = PinHash.hash("0427", iterations = 1_000)
        assertFalse("0427" in stored)
        assertTrue(PinHash.matches("0427", stored))
        assertFalse(PinHash.matches("0428", stored))
    }

    @Test
    fun `deux empreintes du même PIN diffèrent grâce au sel`() {
        assertNotEquals(PinHash.hash("1111", iterations = 1_000), PinHash.hash("1111", iterations = 1_000))
    }

    @Test
    fun `une empreinte abîmée ne correspond à rien`() {
        assertFalse(PinHash.matches("0427", ""))
        assertFalse(PinHash.matches("0427", "md5\$1\$abc\$def"))
        assertFalse(PinHash.matches("0427", "pbkdf2-sha256\$0\$AAAA\$AAAA"))
        assertFalse(PinHash.matches("0427", "pbkdf2-sha256\$1000\$pas du base64\$!!"))
    }

    @Test
    fun `cinq codes faux bloquent cinq minutes, puis le délai double`() {
        var lockout = PinLockout()
        repeat(4) { lockout = lockout.afterFailure(now = 0) }
        assertEquals(1, lockout.triesLeft)
        assertFalse(lockout.isLocked(0))

        lockout = lockout.afterFailure(now = 1_000)
        assertTrue(lockout.isLocked(1_000))
        assertEquals(5 * 60_000L, lockout.remainingMs(1_000))
        assertEquals(PinLockout.MAX_FAILURES, lockout.triesLeft)

        val afterLock = 1_000 + 5 * 60_000L
        assertFalse(lockout.isLocked(afterLock))
        repeat(5) { lockout = lockout.afterFailure(now = afterLock) }
        assertEquals(10 * 60_000L, lockout.remainingMs(afterLock))
    }

    @Test
    fun `le blocage plafonne à une heure`() {
        var lockout = PinLockout(lockouts = 10)
        repeat(5) { lockout = lockout.afterFailure(now = 0) }
        assertEquals(PinLockout.MAX_LOCK_MS, lockout.remainingMs(0))
    }

    @Test
    fun `temps d'attente lisible`() {
        assertEquals("5 min", PinLockout.formatWait(5 * 60_000L))
        assertEquals("1 min 30", PinLockout.formatWait(90_000))
        assertEquals("45 s", PinLockout.formatWait(44_200))
        assertEquals("1 h 00", PinLockout.formatWait(60 * 60_000L))
    }
}
