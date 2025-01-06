package io.socket.java;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.msgpack.MessagePack;
import org.msgpack.packer.Packer;

import io.socket.java.protocol.Packet;
import io.socket.java.protocol.PacketBinary;
import io.socket.java.protocol.PacketJson;
import io.socket.java.protocol.PacketText;
import org.redisson.Redisson;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

public class Emitter {

	/**
	 * Flags.
	 */
	private static enum Flag {
		JSON, VOLATILE, BROADCAST;

		@Override
		public String toString() {
			return name().toLowerCase();
		}
	}

	private static int EVENT = 2;
	private static int BINARY_EVENT = 5;

	private String key;
	private ArrayList<String> rooms = new ArrayList<>();
	private HashMap<String, Object> flags = new HashMap<>();
	private RedissonClient redisson;

	private static Emitter instance = null;

	private Emitter(RedissonClient redisson, Map<String, String> opts) {
		if (opts.containsKey("key")) {
			this.key = opts.get("key") + "#emitter";
		} else {
			this.key = "socket.io#";
		}

		if (redisson == null) {
			if (!opts.containsKey("host"))
				throw new Error("Missing redis `host`");
			if (!opts.containsKey("port"))
				throw new Error("Missing redis `port`");

			Config config = new Config();
			config.useSingleServer()
					.setAddress("redis://" + opts.get("host") + ":" + opts.get("port"));

			this.redisson = Redisson.create(config);
		} else {
			this.redisson = redisson;
		}
	}

	/**
	 * Socket.IO Redis-based emitter.
	 *
	 * @param redisson Redisson client
	 * @param opts     Option values
	 * @return emitter
	 */
	public static synchronized Emitter getInstance(RedissonClient redisson, Map<String, String> opts) {
		if (instance == null) {
			instance = new Emitter(redisson, opts);
		}
		return instance;
	}

	/**
	 * Apply flags from `Socket`.
	 *
	 * @return emitter
	 */
	public Emitter json() {
		return get(Flag.JSON);
	}

	public Emitter _volatile() {
		return get(Flag.VOLATILE);
	}

	public Emitter broadcast() {
		return get(Flag.BROADCAST);
	}

	private Emitter get(Flag flag) {
		this.flags.put(flag.toString(), true);
		return this;
	}

	/**
	 * Limit emission to a certain `room`.
	 */
	public Emitter to(String room) {
		if (!rooms.contains(room)) {
			this.rooms.add(room);
		}
		this.key += room + "#";
		return this;
	}

	public Emitter in(String room) {
		return this.to(room);
	}

	/**
	 * Limit emission to certain `namespace`.
	 */
	public Emitter of(String nsp) {
		this.flags.put("nsp", nsp);
		this.key += nsp + "#";
		return this;
	}

	public Emitter emit(String event, String... data) throws IOException {
		PacketText packet = new PacketText();
		packet.setType(EVENT);

		packet.getData().add(event);
		for (String datum : data) {
			packet.getData().add(datum);
		}

		return this.emit(packet);
	}

	public Emitter emit(String event, JSONObject data) throws IOException {
		PacketJson packet = new PacketJson();
		packet.setType(EVENT);

		packet.setData(data);
		packet.setEvent(event);

		return this.emit(packet);
	}

	public Emitter emit(byte[] b) throws IOException {
		PacketBinary packet = new PacketBinary();
		packet.setType(BINARY_EVENT);
		packet.setData(b);

		return this.emit(packet);
	}

	private Emitter emit(Packet packet) throws IOException {
		if (this.flags.containsKey("nsp")) {
			packet.setNsp((String) this.flags.get("nsp"));
			this.flags.remove("nsp");
		} else {
			packet.setNsp("/");
		}

		packet.setRooms(this.rooms);
		packet.setFlags(this.flags);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		MessagePack msgpack = new MessagePack();

		Packer packer = msgpack.createPacker(out);
		packer.write(packet);

		byte[] msg = out.toByteArray();

		try {
			RTopic topic = redisson.getTopic(this.key);
			topic.publish(msg);
		} catch (Exception e) {
			throw new IOException("Failed to publish message to Redis topic", e);
		}

		// Reset state
		this.rooms = new ArrayList<>();
		this.flags = new HashMap<>();
		this.key = "socket.io#";

		return this;
	}

	/**
	 * Clean up resources.
	 */
	public void shutdown() {
		if (this.redisson != null) {
			this.redisson.shutdown();
		}
	}
}
