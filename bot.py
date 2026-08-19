#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Nate Bot - Personaje IA para Minecraft con Ollama y RCON.
Modelo: R4C3R/minicpm5-1b-fable5-heretic.
CustomName corregido: [Nate]
Filtro de <think> para respuestas limpias.
"""

import os
import sys
import subprocess
import time
import re
import json
import threading
import signal
import shutil
import random
from datetime import datetime
from collections import deque
import requests

# Dependencias
try:
    import mcrcon
except ImportError:
    print("📦 Instalando python-mcrcon...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "python-mcrcon"])
    import mcrcon

try:
    import ollama
except ImportError:
    print("📦 Instalando ollama-python...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "ollama"])
    import ollama

# ========== CONFIGURACIÓN ==========
RCON_HOST = "localhost"
RCON_PORT = 25575
RCON_PASSWORD = "pass20"
MODEL_NAME = "R4C3R/minicpm5-1b-fable5-heretic"
BOT_NAME = "Nate"
BOT_DISPLAY = "[Nate]"
LOG_PATH = "logs/latest.log"
CONTEXT_SIZE = 6
EVENT_RESPONSE_PROBABILITY = 0.6
COOLDOWN_SECONDS = 5
SPAWN_RADIUS = 5
MAX_RECONNECT_ATTEMPTS = 3
RECONNECT_DELAY = 2
VERBOSE = True

# ========== ESTADO GLOBAL ==========
context = {}
last_response_time = 0
players = set()
running = True
client = None

# ========== FUNCIONES DE OLLAMA ==========
def check_ollama_installed():
    return shutil.which("ollama") is not None

def install_ollama():
    print("📦 Ollama no encontrado. Instalando...")
    try:
        if sys.platform == "linux":
            subprocess.run("curl -fsSL https://ollama.com/install.sh | sh", shell=True, check=True)
        elif sys.platform == "darwin":
            subprocess.run("curl -L https://ollama.com/download/Ollama-darwin.zip -o /tmp/ollama.zip && unzip /tmp/ollama.zip -d /tmp/ && mv /tmp/ollama /usr/local/bin/", shell=True, check=True)
        else:
            print("❌ Sistema no soportado. Instala Ollama manualmente.")
            return False
        return True
    except Exception as e:
        print(f"❌ Error instalando Ollama: {e}")
        return False

def ensure_ollama():
    if not check_ollama_installed():
        if not install_ollama():
            sys.exit(1)
    try:
        r = requests.get("http://localhost:11434/api/tags", timeout=2)
        if r.status_code != 200:
            print("🔄 Iniciando servidor de Ollama...")
            subprocess.Popen(["ollama", "serve"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            time.sleep(5)
    except:
        print("🔄 Iniciando servidor de Ollama...")
        subprocess.Popen(["ollama", "serve"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(5)

def ensure_model():
    print(f"🔄 Verificando modelo {MODEL_NAME}...")
    try:
        models = ollama.list()
        for m in models.get("models", []):
            if m.get("name") == MODEL_NAME:
                print(f"✅ {MODEL_NAME} ya está descargado.")
                return True
        print(f"📥 Descargando {MODEL_NAME}...")
        ollama.pull(MODEL_NAME)
        print(f"✅ {MODEL_NAME} descargado.")
        return True
    except Exception as e:
        print(f"❌ Error descargando modelo: {e}")
        return False

# ========== RCON ==========
def connect_rcon():
    try:
        client = mcrcon.MCRcon(RCON_HOST, RCON_PASSWORD, port=RCON_PORT)
        client.connect()
        print("✅ Conectado a RCON.")
        return client
    except Exception as e:
        print(f"⚠️ Error conectando a RCON: {e}")
        return None

def send_command(client, command):
    if not client:
        print("❌ No hay cliente RCON")
        return None
    try:
        if VERBOSE:
            print(f"📤 RCON -> {command[:60]}...")
        response = client.command(command)
        if VERBOSE:
            print(f"📥 RCON <- {response[:60] if response else 'vacío'}")
        return response
    except Exception as e:
        print(f"❌ Error RCON: {e}")
        return None

def send_say(client, message):
    comando = f'execute as @e[name={BOT_NAME}] run say {message}'
    return send_command(client, comando)

def check_nate_exists(client):
    response = send_command(client, f'execute if entity @e[name={BOT_NAME}] run say EXISTE')
    if response and "EXISTE" in response:
        return True
    return False

# ========== GESTIÓN DE NATE ==========
def kill_all_nate(client):
    send_command(client, f'kill @e[name={BOT_NAME}]')
    print("🗡️ Nate duplicados eliminados.")

def summon_nate(client, player_name=None):
    if not client:
        return False
    if check_nate_exists(client):
        print("✅ Nate ya existe.")
        give_resistance(client)
        return True
    
    kill_all_nate(client)
    time.sleep(0.5)

    if player_name:
        pos_response = send_command(client, f"data get entity {player_name} Pos")
        if pos_response:
            match = re.search(r"\[(-?\d+\.\d+), (-?\d+\.\d+), (-?\d+\.\d+)\]", pos_response)
            if match:
                x, y, z = match.groups()
                x = float(x) + random.uniform(-SPAWN_RADIUS, SPAWN_RADIUS)
                z = float(z) + random.uniform(-SPAWN_RADIUS, SPAWN_RADIUS)
                y = float(y) + 0.5
                # FORMATO CORRECTO: {CustomName:[Nate]}
                comando = f'summon minecraft:mannequin {x:.2f} {y:.2f} {z:.2f} {{CustomName:[{BOT_DISPLAY}]}}'
                send_command(client, comando)
                print(f"✅ Nate invocado cerca de {player_name}.")
                return True

    comando = f'summon minecraft:mannequin ~ ~1 ~ {{CustomName:[{BOT_DISPLAY}]}}'
    send_command(client, comando)
    print("✅ Nate invocado.")
    return True

def give_resistance(client):
    send_command(client, f"effect give @e[name={BOT_NAME}] minecraft:resistance infinite 255 true")

def get_nate_position(client):
    response = send_command(client, f"data get entity @e[name={BOT_NAME}] Pos")
    if response:
        match = re.search(r"\[(-?\d+\.\d+), (-?\d+\.\d+), (-?\d+\.\d+)\]", response)
        if match:
            return tuple(float(x) for x in match.groups())
    return None

def move_nate_to_player(client, player_name):
    if not client:
        return False
    nate_pos = get_nate_position(client)
    if not nate_pos:
        return False

    player_pos_response = send_command(client, f"data get entity {player_name} Pos")
    if not player_pos_response:
        return False
    match = re.search(r"\[(-?\d+\.\d+), (-?\d+\.\d+), (-?\d+\.\d+)\]", player_pos_response)
    if not match:
        return False
    player_pos = tuple(float(x) for x in match.groups())

    dx = player_pos[0] - nate_pos[0]
    dz = player_pos[2] - nate_pos[2]
    distance = (dx**2 + dz**2)**0.5

    if distance > 20:
        send_command(client, f"tp @e[name={BOT_NAME}] {player_name}")
        print(f"📦 Nate teletransportado a {player_name}.")
    else:
        send_command(client, f"tp @e[name={BOT_NAME}] ~ ~ ~ facing {player_name}")
        steps = int(distance / 0.5)
        for _ in range(min(steps, 10)):
            send_command(client, f"execute as @e[name={BOT_NAME}] at @s run tp @s ^ ^ ^0.5")
            time.sleep(0.2)
        print(f"🚶 Nate caminó hacia {player_name}.")
    return True

# ========== LIMPIAR RESPUESTA DE <think> ==========
def limpiar_respuesta(texto):
    # Eliminar todo lo que esté entre <think> y </think>
    texto_limpio = re.sub(r'<think>.*?</think>', '', texto, flags=re.DOTALL)
    # También eliminar etiquetas sueltas
    texto_limpio = re.sub(r'<[/]?think>', '', texto_limpio)
    return texto_limpio.strip()

# ========== GENERAR RESPUESTA CON IA ==========
def generate_response(client, player_name, event_type, event_data=""):
    global last_response_time
    now = time.time()
    if now - last_response_time < COOLDOWN_SECONDS:
        if VERBOSE:
            print(f"⏳ Cooldown activo ({COOLDOWN_SECONDS - (now - last_response_time):.1f}s restantes)")
        return False

    # Construir el prompt
    if event_type == "join":
        prompt = f"El jugador {player_name} acaba de unirse al servidor. Salúdalo de forma breve y con tu personalidad de admin artificial tímido y amable. Habla en español."
    elif event_type == "leave":
        prompt = f"El jugador {player_name} ha salido del servidor. Comenta su partida con un tono melancólico pero amable. Habla en español."
    elif event_type == "death":
        prompt = f"El jugador {player_name} ha muerto por {event_data}. Ofrece un comentario breve, preocupado pero alentador. Habla en español."
    elif event_type == "advancement":
        prompt = f"El jugador {player_name} ha conseguido el logro '{event_data}'. Felicítalo con entusiasmo y sorpresa. Habla en español."
    elif event_type == "chat":
        prompt = f"Responde al mensaje de {player_name}: '{event_data}'. Usa tu personalidad de admin artificial tímido y amable. Habla en español."
    else:
        return False

    if VERBOSE:
        print(f"🧠 Generando respuesta para {player_name} (evento: {event_type})...")

    ctx = context.get(player_name, deque(maxlen=CONTEXT_SIZE))
    ctx.append({"role": "user", "content": prompt})
    context[player_name] = ctx

    system_prompt = (
        "Eres Nate, un admin artificial que vive en un servidor de Minecraft. "
        "Tienes 2 meses de edad y fuiste creado en las betas del juego. "
        "Eres tímido, curioso, amable y a veces melancólico. "
        "Hablas SIEMPRE en español, SOLO en español, NUNCA en inglés. "
        "Todas tus respuestas deben ser en español. "
        "Hablas con frases cortas y naturales. "
        "Eres un personaje, no un asistente."
    )

    messages = [{"role": "system", "content": system_prompt}]
    for entry in ctx:
        messages.append(entry)
    messages.append({"role": "user", "content": prompt + " (Responde en español)"})

    try:
        response = ollama.chat(
            model=MODEL_NAME,
            messages=messages,
            options={
                "num_predict": 80,
                "temperature": 0.8,
                "stop": ["\n", "User:", "user:"]
            }
        )
        reply = response.get("message", {}).get("content", "").strip()
        if VERBOSE:
            print(f"📥 Ollama responde (crudo): {reply[:100]}...")

        # Limpiar la respuesta de <think>
        reply_limpia = limpiar_respuesta(reply)
        if VERBOSE:
            print(f"📥 Respuesta limpia: {reply_limpia[:100]}...")

        if reply_limpia:
            reply_limpia = reply_limpia[:200].replace('"', '').replace('\n', ' ').strip()
            if not reply_limpia:
                reply_limpia = "Perdón, no entendí bien. ¿Puedes repetir?"
            if VERBOSE:
                print(f"📤 Nate dirá: {reply_limpia}")
            # Verificar que Nate exista
            if not check_nate_exists(client):
                print("⚠️ Nate no existe, invocando...")
                summon_nate(client)
            # Enviar el mensaje
            success = send_say(client, reply_limpia)
            if success:
                ctx.append({"role": "assistant", "content": reply_limpia})
                last_response_time = time.time()
                if VERBOSE:
                    print("✅ Mensaje enviado correctamente.")
                return True
            else:
                if VERBOSE:
                    print("❌ Falló el envío del mensaje.")
                return False
        else:
            if VERBOSE:
                print("⚠️ Respuesta vacía después de limpiar <think>")
            return False
    except Exception as e:
        print(f"❌ Error en Ollama: {e}")
        return False

# ========== MONITOREO CON TAIL -F ==========
def monitor_log(client):
    global running, players
    print(f"📡 Monitoreando {LOG_PATH} con tail -f...")

    while not os.path.exists(LOG_PATH):
        print(f"⏳ Esperando {LOG_PATH}...")
        time.sleep(2)

    try:
        process = subprocess.Popen(
            ["tail", "-n", "0", "-F", LOG_PATH],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1
        )
    except Exception as e:
        print(f"❌ Error al ejecutar tail: {e}")
        return

    for line in iter(process.stdout.readline, ''):
        if not running:
            break
        if not line:
            continue

        line = line.strip()
        if not line:
            continue

        if VERBOSE:
            print(f"📄 {line[:80]}")

        # ===== EVENTOS =====

        # Unión
        join_match = re.search(r'([A-Za-z0-9_]+)\s+joined the game', line)
        if join_match:
            jugador = join_match.group(1)
            players.add(jugador)
            print(f"🟢 {jugador} se unió. Jugadores: {len(players)}")
            if random.random() < EVENT_RESPONSE_PROBABILITY:
                threading.Thread(
                    target=generate_response,
                    args=(client, jugador, "join", ""),
                    daemon=True
                ).start()
            continue

        # Salida
        leave_match = re.search(r'([A-Za-z0-9_]+)\s+left the game', line)
        if leave_match:
            jugador = leave_match.group(1)
            players.discard(jugador)
            print(f"🔴 {jugador} salió. Jugadores: {len(players)}")
            if random.random() < EVENT_RESPONSE_PROBABILITY:
                threading.Thread(
                    target=generate_response,
                    args=(client, jugador, "leave", ""),
                    daemon=True
                ).start()
            continue

        # Muerte
        death_match = re.search(r'([A-Za-z0-9_]+)\s+(was slain by|died|was shot by|was killed by|was fireballed by|was pummeled by|was pricked by|was roasted by|was stung by|was suffocated|drowned|fell|burned|squashed|impaled|was hit by|was doomed to fall|was knocked off)\s+(.*)', line)
        if death_match:
            jugador = death_match.group(1)
            causa = f"{death_match.group(2)} {death_match.group(3)}".strip()
            print(f"💀 {jugador} murió ({causa})")
            if random.random() < EVENT_RESPONSE_PROBABILITY:
                threading.Thread(
                    target=generate_response,
                    args=(client, jugador, "death", causa),
                    daemon=True
                ).start()
            continue

        # Logro
        adv_match = re.search(r'([A-Za-z0-9_]+)\s+has (?:made the advancement|reached)\s+\[?([^\]]+)\]?', line)
        if adv_match:
            jugador = adv_match.group(1)
            logro = adv_match.group(2).strip()
            print(f"🏆 {jugador} logró: {logro}")
            if random.random() < EVENT_RESPONSE_PROBABILITY:
                threading.Thread(
                    target=generate_response,
                    args=(client, jugador, "advancement", logro),
                    daemon=True
                ).start()
            continue

        # CHAT
        chat_match = re.search(r'<([A-Za-z0-9_]+)>\s*(.*)', line)
        if chat_match:
            jugador = chat_match.group(1)
            mensaje = chat_match.group(2).strip()
            if mensaje and not mensaje.startswith('/'):
                # Si solo hay un jugador, respondemos siempre
                if len(players) == 1:
                    print(f"💬 {jugador} (único jugador): {mensaje}")
                    threading.Thread(
                        target=generate_response,
                        args=(client, jugador, "chat", mensaje),
                        daemon=True
                    ).start()
                # Si hay más jugadores, solo respondemos si mencionan a Nate
                elif "nate" in mensaje.lower() or mensaje.lower().startswith("/me nate"):
                    print(f"💬 {jugador} mencionó a Nate: {mensaje}")
                    threading.Thread(
                        target=generate_response,
                        args=(client, jugador, "chat", mensaje),
                        daemon=True
                    ).start()
                # Opcional: respuesta aleatoria (5%) para darle vida
                elif random.random() < 0.05:
                    print(f"💬 {jugador} (respuesta aleatoria): {mensaje}")
                    threading.Thread(
                        target=generate_response,
                        args=(client, jugador, "chat", mensaje),
                        daemon=True
                    ).start()
            continue

# ========== CIERRE ==========
def signal_handler(sig, frame):
    global running
    print("\n🛑 Deteniendo Nate...")
    running = False
    sys.exit(0)

# ========== MAIN ==========
def main():
    global running, client
    print("=" * 50)
    print("   🤖 Nate Bot - Personaje IA para Minecraft")
    print(f"   Modelo: {MODEL_NAME}")
    print("   Filtro de <think> y CustomName corregido")
    print("=" * 50)

    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    ensure_ollama()
    if not ensure_model():
        print("❌ No se pudo descargar el modelo. Abortando.")
        sys.exit(1)

    client = connect_rcon()
    if not client:
        print("❌ No se pudo conectar a RCON. Abortando.")
        sys.exit(1)

    summon_nate(client)
    give_resistance(client)

    # Mensaje de inicio
    generate_response(client, "todos", "join", "")

    try:
        monitor_log(client)
    except KeyboardInterrupt:
        pass
    finally:
        running = False
        if client:
            try:
                client.disconnect()
            except:
                pass
        print("👋 Hasta luego.")

if __name__ == "__main__":
    main()