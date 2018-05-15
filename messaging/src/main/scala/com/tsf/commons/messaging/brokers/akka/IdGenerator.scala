package com.tsf.commons.messaging.brokers.akka

import java.security.SecureRandom
import java.time.{LocalDateTime, OffsetDateTime, ZoneOffset}
import java.util.concurrent.atomic.AtomicLong

object IdGenerator {
	private[this] val _sequence = new AtomicLong(0)
	private[this] val _random = new SecureRandom()

	def decompose(id: BigInt): (OffsetDateTime, Long, Long) = {
		val ts = (id >> 64).toLong
		val rnd = (id & 0xFFFFFFFF).toLong

		(
			OffsetDateTime.of(
				((ts >> 51) & 0x0FFF).toInt,
				((ts >> 47) & 0x000F).toInt,
				((ts >> 42) & 0x001F).toInt,
				((ts >> 37) & 0x001F).toInt,
				((ts >> 31) & 0x003F).toInt,
				((ts >> 25) & 0x003F).toInt,
				((ts >> 15) & 0x03FF).toInt,
				ZoneOffset.UTC),
			ts & 0x03FF,
			rnd
		)
	}

	protected[messaging] def getMaxIdAtTime(date: OffsetDateTime): BigInt = generateId(date, (1 << 15) - 1, 0xFFFFFFFFFFFFFFFFL)

	def generateId(): BigInt = generateId(OffsetDateTime.now(ZoneOffset.UTC), _sequence.incrementAndGet(), _random.nextLong())

	private def generateId(date: OffsetDateTime, seq: Long, rnd: Long): BigInt = {
		val utc =
			if (date.getOffset == ZoneOffset.UTC) {
				date.toLocalDateTime
			} else {
				LocalDateTime.ofInstant(date.toInstant, date.getOffset)
			}

		// [0][year:12][month:4][day:5][hour:5][minute:6][second:6][ms:10][sequence:15]
		val timestamp = (
			((utc.getYear: Long@unchecked) & 0x0FFF) << 51
				| (utc.getMonthValue: Long@unchecked) << 47
				| (utc.getDayOfMonth: Long@unchecked) << 42
				| (utc.getHour: Long@unchecked) << 37
				| (utc.getMinute: Long@unchecked) << 31
				| (utc.getSecond: Long@unchecked) << 25
				| ((utc.getNano: Long@unchecked) / 1000000L) << 15
				| seq % 32767
			)

		(BigInt(timestamp) << 64) + BigInt(rnd)
	}
}