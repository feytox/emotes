package io.github.kosmx.emotes.server.serializer;

import com.google.gson.*;
import io.github.kosmx.emotes.common.emote.EmoteData;
import io.github.kosmx.emotes.common.emote.EmoteFormat;
import io.github.kosmx.emotes.common.opennbs.NBSFileUtils;
import io.github.kosmx.emotes.common.quarktool.QuarkReader;
import io.github.kosmx.emotes.common.tools.Easing;
import io.github.kosmx.emotes.common.tools.MathHelper;
import io.github.kosmx.emotes.common.tools.UUIDMap;
import io.github.kosmx.emotes.executor.EmoteInstance;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.logging.Level;


public class EmoteSerializer implements JsonDeserializer<List<EmoteData>>, JsonSerializer<EmoteData> {


    private final int modVersion = 3;

    public static void serializeEmotes(UUIDMap<EmoteData> emotes, File externalEmotes){
        if(!externalEmotes.isDirectory()){
            externalEmotes.mkdir();
        }

        for(File file : Objects.requireNonNull(externalEmotes.listFiles((dir, name)->name.endsWith(".json")))){
            emotes.addAll(serializeExternalEmote(file));
        }
        for(File file : Objects.requireNonNull(externalEmotes.listFiles((dir, name)->name.endsWith("." + EmoteFormat.BINARY.getExtension())))){
            emotes.addAll(serializeExternalEmote(file));
        }

        if(EmoteInstance.config.enableQuark.get()){
            EmoteInstance.instance.getLogger().log(Level.INFO, "Quark importer is active", true);
            for(File file : Objects.requireNonNull(externalEmotes.listFiles((dir, name)->name.endsWith(".emote")))){
                emotes.addAll(serializeExternalEmote(file));
            }
        }
    }

    private static List<EmoteData> serializeExternalEmote(File file){
        File externalEmotes = EmoteInstance.instance.getExternalEmoteDir();
        List<EmoteData> emotes = new LinkedList<>();
        try{
            InputStream reader = Files.newInputStream(file.toPath());
            emotes = UniversalEmoteSerializer.readData(reader, file.getName());
            //EmoteHolder.addEmoteToList(emotes);
            reader.close();
            Path icon = externalEmotes.toPath().resolve(file.getName().substring(0, file.getName().length() - 5) + ".png");

            if(icon.toFile().isFile()){
                InputStream iconStream = Files.newInputStream(icon);
                emotes.forEach(emote -> {
                    try {
                        emote.iconData = MathHelper.readFromIStream(iconStream);
                        iconStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }

            File song = externalEmotes.toPath().resolve(file.getName().substring(0, file.getName().length() - 5) + ".nbs").toFile();
            if(song.isFile() && emotes.size() == 1){
                DataInputStream bis = new DataInputStream(new FileInputStream(song));
                try {
                    emotes.get(0).song = NBSFileUtils.read(bis);
                }
                catch (IOException exception){
                    EmoteInstance.instance.getLogger().log(Level.WARNING, "Error while reading song: " + exception.getMessage(), true);
                    if(EmoteInstance.config.showDebug.get()) exception.printStackTrace();
                }
                bis.close(); //I almost forgot this
            }
        }catch(Exception e){
            EmoteInstance.instance.getLogger().log(Level.WARNING, "Error while importing external emote: " + file.getName() + ".", true);
            EmoteInstance.instance.getLogger().log(Level.WARNING, e.getMessage());
            if(EmoteInstance.config.showDebug.get())e.printStackTrace();
        }
        return emotes;
    }

    @Override
    public List<EmoteData> deserialize(JsonElement p, Type typeOf, JsonDeserializationContext ctxt) throws JsonParseException{
        JsonObject node = p.getAsJsonObject();

        if(!node.has("emote")){
            return GeckoLibSerializer.serialize(node);
        }

        int version = 1;
        if(node.has("version")) version = node.get("version").getAsInt();
        //Text author = EmoteInstance.instance.getDefaults().emptyTex();
        EmoteData.EmoteBuilder emote = emoteDeserializer(node.getAsJsonObject("emote"), version);


        //Text name = EmoteInstance.instance.getDefaults().fromJson(node.get("name"));
        emote.name = node.get("name").toString();
        if(node.has("author")){
            emote.author = node.get("author").toString();
        }

        if(node.has("uuid")){
            emote.uuid = UUID.fromString(node.get("uuid").getAsString());
        }

        if(modVersion < version){
            EmoteInstance.instance.getLogger().log(Level.WARNING, "Emote: " + emote.name + " was made for a newer mod version", true);
            throw new JsonParseException(emote.name + " is version " + Integer.toString(version) + ". Emotecraft can only process version " + Integer.toString(modVersion) + ".");
        }

        if(node.has("description")){
            emote.description = node.get("description").toString();
        }
        node.entrySet().forEach((entry)->{
            String string = entry.getKey();
            if(string.equals("uuid") || string.equals("author") || string.equals("comment") || string.equals("name") || string.equals("description") || string.equals("emote") || string.equals("version"))
                return;
            EmoteInstance.instance.getLogger().log(Level.WARNING, "Can't understadt: " + string + " : " + entry.getValue());
            EmoteInstance.instance.getLogger().log(Level.WARNING, "If it is a comment, ignore the warning");
        });

        emote.optimizeEmote();
        List<EmoteData> list = new ArrayList<>();
        list.add(emote.build());
        return list;
    }

    private EmoteData.EmoteBuilder emoteDeserializer(JsonObject node, int version) throws JsonParseException{
        EmoteData.EmoteBuilder builder = new EmoteData.EmoteBuilder(EmoteFormat.JSON_EMOTECRAFT);
        if(node.has("beginTick")){
            builder.beginTick = node.get("beginTick").getAsInt();
        }
        builder.endTick = node.get("endTick").getAsInt();
        if(builder.endTick <= 0) throw new JsonParseException("endTick must be bigger than 0");
        if(node.has("isLoop") && node.has("returnTick")){
            builder.isLooped = node.get("isLoop").getAsBoolean();
            builder.returnTick = node.get("returnTick").getAsInt();
            if(builder.isLooped && (builder.returnTick > builder.endTick || builder.returnTick < 0))
                throw new JsonParseException("return tick have to be smaller than endTick and not smaller than 0");
        }

        if(node.has("nsfw")){
            builder.nsfw = node.get("nsfw").getAsBoolean();
        }

        node.entrySet().forEach((entry)->{
            String string = entry.getKey();
            if(string.equals("beginTick") || string.equals("comment") || string.equals("endTick") || string.equals("stopTick") || string.equals("degrees") || string.equals("moves") || string.equals("returnTick") || string.equals("isLoop") || string.equals("easeBeforeKeyframe") || string.equals("nsfw"))
                return;
            EmoteInstance.instance.getLogger().log(Level.WARNING, "Can't understadt: " + string + " : " + entry.getValue());
            EmoteInstance.instance.getLogger().log(Level.WARNING, "If it is a comment, ignore the warning");
        });
        builder.stopTick = node.has("stopTick") ? node.get("stopTick").getAsInt() : builder.endTick;
        boolean degrees = ! node.has("degrees") || node.get("degrees").getAsBoolean();
        //EmoteData emote = new EmoteData(beginTick, endTick, resetTick, isLoop, returnTick);
        if(node.has("easeBeforeKeyframe"))builder.isEasingBefore = node.get("easeBeforeKeyframe").getAsBoolean();
        moveDeserializer(builder, node.getAsJsonArray("moves"), degrees, version);

        builder.fullyEnableParts();

        return builder;
    }

    private void moveDeserializer(EmoteData.EmoteBuilder emote, JsonArray node, boolean degrees, int version){
        for(JsonElement n : node){
            JsonObject obj = n.getAsJsonObject();
            int tick = obj.get("tick").getAsInt();
            String easing = obj.has("easing") ? obj.get("easing").getAsString() : "linear";
            int turn = obj.has("turn") ? obj.get("turn").getAsInt() : 0;
            for(Map.Entry<String, JsonElement> entry:obj.entrySet()){
                if(entry.getKey().equals("tick") || entry.getKey().equals("comment") || entry.getKey().equals("easing") || entry.getKey().equals("turn")){
                    continue;
                }
                addBodyPartIfExists(emote, entry.getKey(), entry.getValue(), degrees, tick, easing, turn, version);
            }
        }
    }

    private void addBodyPartIfExists(EmoteData.EmoteBuilder emote, String name, JsonElement node, boolean degrees, int tick, String easing, int turn, int version){
        if(version < 3 && name.equals("torso"))name = "body";// rename part
        EmoteData.StateCollection part = emote.getPart(name);
        if(part == null){
            EmoteInstance.instance.getLogger().log(Level.WARNING, "Can't understadt: " + name + " : " + node);
            EmoteInstance.instance.getLogger().log(Level.WARNING, "If it is a comment, ignore the warning");
            return;
        }
        JsonObject partNode = node.getAsJsonObject();
        partNode.entrySet().forEach((entry)->{
            String string = entry.getKey();
            if(string.equals("x") || string.equals("y") || string.equals("z") || string.equals("pitch") || string.equals("yaw") || string.equals("roll") || string.equals("comment") || string.equals("bend") || string.equals("axis"))
                return;
            EmoteInstance.instance.getLogger().log(Level.WARNING, "Can't understadt: " + string + " : " + entry.getValue());
            EmoteInstance.instance.getLogger().log(Level.WARNING, "If it is a comment, ignore the warning");
        });
        addPartIfExists(part.x, "x", partNode, degrees, tick, easing, turn);
        addPartIfExists(part.y, "y", partNode, degrees, tick, easing, turn);
        addPartIfExists(part.z, "z", partNode, degrees, tick, easing, turn);
        addPartIfExists(part.pitch, "pitch", partNode, degrees, tick, easing, turn);
        addPartIfExists(part.yaw, "yaw", partNode, degrees, tick, easing, turn);
        addPartIfExists(part.roll, "roll", partNode, degrees, tick, easing, turn);
        addPartIfExists(part.bend, "bend", partNode, degrees, tick, easing, turn);
        addPartIfExists(part.bendDirection, "axis", partNode, degrees, tick, easing, turn);
    }

    private void addPartIfExists(EmoteData.StateCollection.State part, String name, JsonObject node, boolean degrees, int tick, String easing, int turn){
        if(node.has(name)){
            part.addKeyFrame(tick, node.get(name).getAsFloat(), Easing.easeFromString(easing), turn, degrees);
        }
    }




    /**
     * To serialize emotes to Json.
     * This code is not used in the mod, but I left it here for modders.
     *
     * If you want to serialize an emote without EmoteHolder
     * do new EmoteHolder(emote, new LiteralText("name").formatted(Formatting.WHITE), new LiteralText("someString").formatted(Formatting.GRAY), new LiteralText("author").formatted(Formatting.GRAY), some random hash(int));
     * (this code is from {@link QuarkReader#getEmote()})
     *
     * or use {@link EmoteSerializer#emoteSerializer(EmoteData)}
     *
     *
     * @param emote source EmoteData
     * @param typeOfSrc idk
     * @param context :)
     * @return :D
     * Sorry for these really... useful comments
     */
    @Override
    public JsonElement serialize(EmoteData emote, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject node = new JsonObject();
        node.addProperty("version", emote.isEasingBefore ? 2 : 1); //to make compatible emotes. I won't do it.
        node.add("name", asJson(emote.name));
        node.add("description", asJson(emote.description)); // :D
        if(emote.author != null){
            node.add("author", asJson(emote.author));
        }
        node.add("emote", emoteSerializer(emote));
        return node;
    }

    public static JsonElement asJson(String str){
        return new JsonParser().parse(str);
    }

    /**
     * serialize an emote to json
     * It won't be the same json file (not impossible) but multiple jsons can mean the same emote...
     *
     * Oh, and it's public and static, so you can call it from anywhere.
     *
     * @param emote Emote to serialize
     * @return return Json object
     */
    public static JsonObject emoteSerializer(EmoteData emote){
        JsonObject node = new JsonObject();
        node.addProperty("beginTick", emote.beginTick);
        node.addProperty("endTick", emote.endTick);
        node.addProperty("stopTick", emote.stopTick);
        node.addProperty("isLoop", emote.isInfinite);
        node.addProperty("returnTick", emote.returnToTick);
        node.addProperty("nsfw", emote.nsfw);
        node.addProperty("degrees", false); //No program uses degrees.
        if(emote.isEasingBefore)node.addProperty("easeBeforeKeyframe", true);
        node.add("moves", moveSerializer(emote));
        return node;
    }

    public static JsonArray moveSerializer(EmoteData emote){
        JsonArray node = new JsonArray();
        emote.bodyParts.forEach(new BiConsumer<String, EmoteData.StateCollection>() {
            @Override
            public void accept(String s, EmoteData.StateCollection stateCollection) {
                bodyPartDeserializer(node, stateCollection);
            }
        });
        return node;
    }

    /*
     * from here and below the methods are not public
     * these are really depend on the upper method and I don't think anyone will use these.
     */
    private static void bodyPartDeserializer(JsonArray node, EmoteData.StateCollection bodyPart){
        partDeserialize(node, bodyPart.x, bodyPart.name);
        partDeserialize(node, bodyPart.y, bodyPart.name);
        partDeserialize(node, bodyPart.z, bodyPart.name);
        partDeserialize(node, bodyPart.pitch, bodyPart.name);
        partDeserialize(node, bodyPart.yaw, bodyPart.name);
        partDeserialize(node, bodyPart.roll, bodyPart.name);
        if(bodyPart.isBendable) {
            partDeserialize(node, bodyPart.bend, bodyPart.name);
            partDeserialize(node, bodyPart.bendDirection, bodyPart.name);
        }
    }

    private static void partDeserialize(JsonArray array, EmoteData.StateCollection.State part, String parentName){
        for(EmoteData.KeyFrame keyFrame : part.keyFrames){
            JsonObject node = new JsonObject();
            node.addProperty("tick", keyFrame.tick);
            node.addProperty("easing", keyFrame.ease.toString());
            JsonObject jsonMove = new JsonObject();
            jsonMove.addProperty(part.name, keyFrame.value);
            node.add(parentName, jsonMove);
            array.add(node);
        }
    }
}
