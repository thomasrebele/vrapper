package net.sourceforge.vrapper.vim.modes.commandline;

import static net.sourceforge.vrapper.keymap.vim.ConstructorWrappers.parseKeyStrokes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Queue;

import net.sourceforge.vrapper.keymap.HashMapState;
import net.sourceforge.vrapper.keymap.KeyMap;
import net.sourceforge.vrapper.keymap.KeyStroke;
import net.sourceforge.vrapper.keymap.Remapping;
import net.sourceforge.vrapper.keymap.SimpleRemapping;
import net.sourceforge.vrapper.keymap.SpecialKey;
import net.sourceforge.vrapper.keymap.State;
import net.sourceforge.vrapper.keymap.Transition;
import net.sourceforge.vrapper.keymap.vim.ConstructorWrappers;
import net.sourceforge.vrapper.keymap.vim.SimpleKeyStroke;
import net.sourceforge.vrapper.vim.EditorAdaptor;
import net.sourceforge.vrapper.vim.Options;
import net.sourceforge.vrapper.vim.modes.AbstractVisualMode;
import net.sourceforge.vrapper.vim.modes.KeyMapResolver;
import net.sourceforge.vrapper.vim.modes.NormalMode;

public abstract class KeyMapper implements Evaluator {

    private static final KeyStroke LEADER_KEY = new SimpleKeyStroke(SpecialKey.LEADER);

    final String[] keymaps;

    public KeyMapper(String... keymaps) {
        this.keymaps = keymaps;
    }

    public static class Map extends KeyMapper {

        private final boolean recursive;

        public Map(boolean recursive, String... keymaps) {
            super(keymaps);
            this.recursive = recursive;
        }

        public Object evaluate(EditorAdaptor vim, Queue<String> command) {
            String lhs = command.poll();
            String rhs = "";
            while( ! command.isEmpty()) {
                //restore spaces between extra parameters
                rhs += command.poll() + " ";
            }
            if (lhs != null && ! "".equals(rhs)) {
                boolean useRecursive = recursive;

                // Simple prefix maps are non-recursive, e.g. nmap n nzz - Vim detects this as well.
                if (recursive && rhs.startsWith(lhs)) {
                    useRecursive = false;
                    vim.getUserInterfaceService().setInfoMessage("Changing recursive remap '" + lhs
                            + "' to non-recursive.");
                }
                String mapLeader = vim.getConfiguration().get(Options.MAPLEADER);
                List<KeyStroke> leaderKeys = new ArrayList<KeyStroke>();
                for (KeyStroke keystroke : parseKeyStrokes(mapLeader)) {
                    leaderKeys.add(keystroke);
                }
                rhs = rhs.trim();
                Iterable<KeyStroke> lhsKeyStrokes = replaceLeader(parseKeyStrokes(lhs), leaderKeys);
                Iterable<KeyStroke> rhsKeyStrokes = replaceLeader(parseKeyStrokes(rhs), leaderKeys);

                for (String name : keymaps) {
                    KeyMap map = vim.getKeyMapProvider().getKeyMap(name);
                    map.addMapping( lhsKeyStrokes, new SimpleRemapping(rhsKeyStrokes, useRecursive));
                }
            }
            // print all mappings
            if("".equals(rhs)) {
            	StringBuilder sb = new StringBuilder();
            	// header
            	sb.append("mode key mapping");
            	
            	// global mapping
            	for(Entry<KeyStroke,KeyStroke> key : KeyMap.GLOBAL_MAP.entrySet()) {
            		sb.append("    " + toPrettyString(key.getKey()) + "    " + toPrettyString(key.getValue()) + "\n");
            	}
            	
            	// iterate over visual, normal, operator, ...
            	for (String name : keymaps) {
                    KeyMap map = vim.getKeyMapProvider().getKeyMap(name);
                    String mapName = name;
                    if(name.equals(AbstractVisualMode.KEYMAP_NAME)) {
                    	mapName = "v";
                    } else if(name.equals(NormalMode.KEYMAP_NAME)) {
                    	mapName = "n";
                    } else if(name.equals(KeyMapResolver.OMAP_NAME)) {
                    	mapName = "o";
                    }
                
                	printMapping(map, sb, mapName + "   ");
                }
            	// TODO: visualize
            	System.err.println(sb);
            }
            
            return null;
        }
        
        /** Print all the mappings of KeyMap */
        protected void printMapping(KeyMap map, StringBuilder sb, String prefix) {
        	for(KeyStroke key : map.supportedKeys()) {
        		Transition<Remapping> t = map.press(key);
            	State<Remapping> s = t.getNextState();
            	if(s != null && s instanceof HashMapState<?>) {
            		HashMapState<Remapping> submap = (HashMapState<Remapping>)s;
            		printMapping(submap, sb, prefix + toPrettyString(key));
            	}
            	sb.append(prefix + toPrettyString(key) + "    " + toPrettyString(t.getValue()) + "\n");
            }
        }
        
        /** Print all the sub-mappings */
        private void printMapping(HashMapState<Remapping> map,  StringBuilder sb, String prefix) {
        	for(KeyStroke subkey : map.supportedKeys()) {		
	        	Transition<Remapping> t = map.press(subkey);
	        	State<Remapping> s = t.getNextState();
	        	if(s != null && s instanceof HashMapState<?>) {
	        		HashMapState<Remapping> submap = (HashMapState<Remapping>)s;
	        		printMapping(submap, sb, prefix + toPrettyString(subkey));
	        	}
	        	sb.append(prefix + toPrettyString(subkey) + "    " + toPrettyString(t.getValue()) + "\n");
        	}
		}
        
        /** Helper function for nicer output */
        private String toPrettyString(Object o) {
        	if(o instanceof SimpleKeyStroke) {
        		String k = "" + ((SimpleKeyStroke) o).getKeyString();
        		return k.length() == 1 ? k : "<" + k + ">";
        	}
        	else if(o instanceof SimpleRemapping) {
        		StringBuilder sb = new StringBuilder();
        		for(KeyStroke key : ((SimpleRemapping) o).getKeyStrokes()) {
        			sb.append(toPrettyString(key));
        		}
        		return sb.toString();
        	}
        	else {
        		System.out.println("don't know how to treat " + o.getClass());
        	}
        	return "" + o;
        }

		protected static Iterable<KeyStroke> replaceLeader(Iterable<KeyStroke> inputKeys,
                Collection<KeyStroke> leaderKeys) {

            // No use checking for <Leader> when 'mapleader' is empty. This behavior mimicks Vim:
            // :nmap <Leader>b B won't do anything unless you also have :nmap b <Leader>b .

            if ( ! leaderKeys.iterator().hasNext()) {
                return inputKeys;
            }

            List<KeyStroke> result = new ArrayList<KeyStroke>();
            for (KeyStroke keystroke : inputKeys) {
                if (keystroke != null) {
                    if (keystroke.equals(LEADER_KEY)) {
                        result.addAll(leaderKeys);
                    } else {
                        result.add(keystroke);
                    }
                }
            }
            return result;
        }
    }

    public static class Unmap extends KeyMapper {

        public Unmap(String... keymaps) {
            super(keymaps);
        }

        public Object evaluate(EditorAdaptor vim, Queue<String> command) {
            if (!command.isEmpty()) {
                Iterable<KeyStroke> mapping = ConstructorWrappers.parseKeyStrokes(command.poll());
                for (String name : keymaps) {
                    KeyMap map = vim.getKeyMapProvider().getKeyMap(name);
                    map.removeMapping(mapping);
                }
            }
            return null;
        }
    }

    public static class Clear extends KeyMapper {

        public Clear(String... keymaps) {
            super(keymaps);
        }

        public Object evaluate(EditorAdaptor vim, Queue<String> command) {
            for (String name : keymaps) {
                KeyMap map = vim.getKeyMapProvider().getKeyMap(name);
                map.clear();
            }
            return null;
        }
    }
}
