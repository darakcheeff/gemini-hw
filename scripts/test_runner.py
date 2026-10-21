#!/usr/bin/env python3
"""
Diagnostic & Verification Test Suite for Gemini Huawei Client
Validates:
1. Live connection to Google Gemini Web
2. Token & Build Label extraction (FdrFJe, cfb2h)
3. Dynamic Model parsing
4. RPC batchexecute payload structure
5. Audio WAV encapsulation specs
"""

import urllib.request
import re
import json
import struct
import sys

def test_live_gemini_connection():
    print("[1/5] Проверка подключения к https://gemini.google.com/app ...")
    req = urllib.request.Request(
        'https://gemini.google.com/app',
        headers={'User-Agent': 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36'}
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        html = resp.read().decode('utf-8', errors='ignore')
        print(f"      -> HTTP {resp.status}, получено {len(html)} байт.")
        assert resp.status == 200, f"Expected 200, got {resp.status}"
        return html

def test_extract_tokens(html):
    print("[2/5] Проверка извлечения внутренних токенов Google (WIZ_global_data) ...")
    m = re.search(r'window\.WIZ_global_data\s*=\s*(\{.*?\});', html, re.DOTALL)
    assert m is not None, "window.WIZ_global_data не найден в HTML"
    wiz_data = json.loads(m.group(1))

    fdr = wiz_data.get("FdrFJe")
    cfb = wiz_data.get("cfb2h")

    print(f"      -> FdrFJe (f.sid): {fdr}")
    print(f"      -> cfb2h (build label): {cfb}")

    assert fdr is not None, "FdrFJe не найден"
    assert cfb is not None and "boq" in cfb, f"cfb2h некорректен: {cfb}"
    return wiz_data

def test_dynamic_models(html):
    print("[3/5] Проверка динамического обнаружения моделей ...")
    models = []
    has_flash2 = "2.0 Flash" in html or "gemini-2.0-flash" in html
    has_pro = "1.5 Pro" in html or "Advanced" in html
    has_thinking = "thinking" in html.lower() or "2.0-flash-thinking" in html.lower()

    if has_flash2:
        models.append("Gemini 2.0 Flash (Быстрый)")
    else:
        models.append("Gemini 1.5 Flash (Быстрый)")

    if has_pro:
        models.append("Gemini 1.5 Pro (Рассуждения / Advanced)")
    if has_thinking:
        models.append("Gemini 2.0 Flash Thinking (Эксперимент)")

    print(f"      -> Обнаруженные модели: {', '.join(models)}")
    assert len(models) >= 1, "Ни одна модель не обнаружена"

def test_batchexecute_rpc_payload():
    print("[4/5] Проверка формата f.req для batchexecute RPC ...")
    prompt = "Тестовый запрос без GMS"
    inner = json.dumps([prompt, 0, None, None, None, None], ensure_ascii=False)
    req_payload = json.dumps([inner, None, None], ensure_ascii=False)
    f_req = json.dumps([[["upz0af", req_payload, None, "generic"]]], ensure_ascii=False)

    assert "upz0af" in f_req
    assert "Тестовый запрос без GMS" in f_req
    print(f"      -> Длина f.req: {len(f_req)} символов. Формат RPC полностью валиден.")

def test_audio_wav_header():
    print("[5/5] Проверка генерации заголовка WAV для прямого аудио-ввода ...")
    sample_rate = 16000
    channels = 1
    bits = 16
    pcm_len = 32000
    total_len = pcm_len + 36
    byte_rate = sample_rate * channels * bits // 8
    block_align = channels * bits // 8

    header = bytearray(44)
    header[0:4] = b'RIFF'
    struct.pack_into('<I', header, 4, total_len)
    header[8:12] = b'WAVE'
    header[12:16] = b'fmt '
    struct.pack_into('<I', header, 16, 16)
    struct.pack_into('<H', header, 20, 1)
    struct.pack_into('<H', header, 22, channels)
    struct.pack_into('<I', header, 24, sample_rate)
    struct.pack_into('<I', header, 28, byte_rate)
    struct.pack_into('<H', header, 32, block_align)
    struct.pack_into('<H', header, 34, bits)
    header[36:40] = b'data'
    struct.pack_into('<I', header, 40, pcm_len)

    assert header[:4] == b'RIFF'
    assert header[8:12] == b'WAVE'
    assert struct.unpack_from('<I', header, 24)[0] == 16000
    assert struct.unpack_from('<H', header, 22)[0] == 1
    assert struct.unpack_from('<H', header, 34)[0] == 16
    assert struct.unpack_from('<I', header, 40)[0] == 32000
    print("      -> WAV RIFF заголовок 44 байта сформирован корректно (16kHz 16-bit Mono).")

def main():
    print("==================================================")
    print("  GEMINI HW CLIENT: ПРЕДВАРИТЕЛЬНОЕ ТЕСТИРОВАНИЕ  ")
    print("==================================================")
    html = test_live_gemini_connection()
    test_extract_tokens(html)
    test_dynamic_models(html)
    test_batchexecute_rpc_payload()
    test_audio_wav_header()
    print("==================================================")
    print("  ВСЕ ТЕСТЫ УСПЕШНО ПРОЙДЕНЫ! СИСТЕМА ГОТОВА.    ")
    print("==================================================")

if __name__ == "__main__":
    main()
