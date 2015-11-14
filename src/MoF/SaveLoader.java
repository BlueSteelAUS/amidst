package MoF;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.jnbt.CompoundTag;
import org.jnbt.DoubleTag;
import org.jnbt.ListTag;
import org.jnbt.NBTInputStream;
import org.jnbt.NBTOutputStream;
import org.jnbt.Tag;

import amidst.Util;
import amidst.logging.Log;
import amidst.map.MapObjectPlayer;

public class SaveLoader {
	public static enum WorldType {
		// @formatter:off
		DEFAULT("Default", "default"),
		FLAT("Flat", "flat"),
		LARGE_BIOMES("Large Biomes", "largeBiomes"),
		AMPLIFIED("Amplified", "amplified"),
		CUSTOMIZED("Customized", "customized");
		// @formatter:on

		public static WorldType from(String nameOrValue) {
			WorldType result = findInstance(nameOrValue);
			if (result == null) {
				Log.crash("Unable to find World Type: " + nameOrValue);
			}
			return result;
		}

		private static WorldType findInstance(String nameOrValue) {
			for (WorldType worldType : values()) {
				if (worldType.name.equalsIgnoreCase(nameOrValue)
						|| worldType.value.equalsIgnoreCase(nameOrValue)) {
					return worldType;
				}
			}
			return null;
		}

		private String name;
		private String value;

		private WorldType(String name, String value) {
			this.name = name;
			this.value = value;
		}

		public String getName() {
			return name;
		}

		public String getValue() {
			return value;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	// @formatter:off
	public static final WorldType[] SELECTABLE_WORLD_TYPES = {
			WorldType.DEFAULT,
			WorldType.FLAT,
			WorldType.LARGE_BIOMES,
			WorldType.AMPLIFIED
	};
	// @formatter:on

	public static WorldType genType = WorldType.DEFAULT;

	public static SaveLoader newInstance(File file) {
		if (file.isDirectory()) {
			return new SaveLoader(new File(file.getAbsoluteFile()
					+ "/level.dat"));
		} else {
			return new SaveLoader(file);
		}
	}

	private List<MapObjectPlayer> players = new ArrayList<MapObjectPlayer>();
	private List<String> back = new ArrayList<String>();

	private File file;

	private long seed;
	private boolean multi;
	private String generatorOptions = "";

	private SaveLoader(File file) {
		this.file = file;
		try {
			load();
		} catch (Exception e) {
			Util.showError(e);
		}
	}

	private void load() throws IOException, FileNotFoundException {
		NBTInputStream inStream = new NBTInputStream(new FileInputStream(file));
		CompoundTag root = (CompoundTag) ((CompoundTag) inStream.readTag())
				.getValue().get("Data");
		inStream.close();
		seed = (Long) (root.getValue().get("RandomSeed").getValue());
		if (root.getValue().get("generatorName") != null) {
			genType = WorldType.from((String) (root.getValue().get(
					"generatorName").getValue()));

			if (genType == WorldType.CUSTOMIZED) {
				generatorOptions = (String) root.getValue()
						.get("generatorOptions").getValue();
			}
		}
		CompoundTag playerTag = (CompoundTag) root.getValue().get("Player");

		File playersFolder = new File(file.getParent(), "players");
		boolean multi = (playersFolder.exists() && (playersFolder.listFiles().length > 0));

		if (multi) {
			Log.i("Multiplayer map detected.");
		} else {
			Log.i("Singleplayer map detected.");
		}

		if (!multi) {
			addPlayer("Player", playerTag);
		} else {
			File[] listing = playersFolder.listFiles();
			for (int i = 0; i < (listing != null ? listing.length : 0); i++) {
				if (listing[i].isFile()) {
					NBTInputStream playerInputStream = new NBTInputStream(
							new FileInputStream(listing[i]));
					addPlayer(listing[i].getName().split("\\.")[0],
							(CompoundTag) ((CompoundTag) playerInputStream
									.readTag()));
					playerInputStream.close();
				}
			}

		}
	}

	public void movePlayer(String name, int x, int y) {
		File out;
		if (multi) {
			String outPath = file.getParent() + "/players/" + name + ".dat";
			out = new File(outPath);
			backupFile(out);
			try {
				NBTInputStream inStream = new NBTInputStream(
						new FileInputStream(out));
				CompoundTag root = (CompoundTag) inStream.readTag();
				inStream.close();

				HashMap<String, Tag> rootMap = new HashMap<String, Tag>(
						root.getValue());
				ArrayList<Tag> posTag = new ArrayList<Tag>(
						((ListTag) rootMap.get("Pos")).getValue());
				posTag.set(0, new DoubleTag("x", x));
				posTag.set(1, new DoubleTag("y", 120));
				posTag.set(2, new DoubleTag("z", y));
				rootMap.put("Pos", new ListTag("Pos", DoubleTag.class, posTag));
				root = new CompoundTag("Data", rootMap);
				NBTOutputStream outStream = new NBTOutputStream(
						new FileOutputStream(out));
				outStream.writeTag(root);
				outStream.close();
			} catch (Exception e) {
				e.printStackTrace();
			}

		} else {
			out = file;
			backupFile(out);
			try {
				NBTInputStream inStream = new NBTInputStream(
						new FileInputStream(out));
				CompoundTag root = (CompoundTag) (((CompoundTag) inStream
						.readTag()).getValue().get("Data"));
				inStream.close();

				HashMap<String, Tag> rootMap = new HashMap<String, Tag>(
						root.getValue());
				HashMap<String, Tag> playerMap = new HashMap<String, Tag>(
						((CompoundTag) rootMap.get("Player")).getValue());
				ArrayList<Tag> posTag = new ArrayList<Tag>(
						((ListTag) playerMap.get("Pos")).getValue());
				posTag.set(0, new DoubleTag("x", x));
				posTag.set(1, new DoubleTag("y", 120));
				posTag.set(2, new DoubleTag("z", y));
				rootMap.put("Player", new CompoundTag("Player", playerMap));
				playerMap.put("Pos",
						new ListTag("Pos", DoubleTag.class, posTag));
				root = new CompoundTag("Data", rootMap);
				HashMap<String, Tag> base = new HashMap<String, Tag>();
				base.put("Data", root);
				root = new CompoundTag("Base", base);
				NBTOutputStream outStream = new NBTOutputStream(
						new FileOutputStream(out));
				outStream.writeTag(root);
				outStream.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void backupFile(File inputFile) {
		File backupFolder = new File(inputFile.getParentFile()
				+ "/amidst_backup/");
		if (!backupFolder.exists()) {
			backupFolder.mkdir();
		}

		File outputFile = new File(backupFolder + "/" + inputFile.getName());
		if (!back.contains(outputFile.toString())) {
			try {
				FileReader in = new FileReader(inputFile);
				FileWriter out = new FileWriter(outputFile);
				int c;

				while ((c = in.read()) != -1) {
					out.write(c);
				}

				in.close();
				out.close();
				back.add(outputFile.toString());
			} catch (Exception ignored) {
			}
		}
	}

	private void addPlayer(String name, CompoundTag ps) {
		List<Tag> pos = ((ListTag) (ps.getValue().get("Pos"))).getValue();
		double x = (Double) pos.get(0).getValue();
		double z = (Double) pos.get(2).getValue();
		players.add(new MapObjectPlayer(name, (int) x, (int) z));
	}

	public List<MapObjectPlayer> getPlayers() {
		return players;
	}

	public String getGeneratorOptions() {
		return generatorOptions;
	}

	public long getSeed() {
		return seed;
	}
}
