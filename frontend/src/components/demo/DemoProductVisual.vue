<template>
  <div class="demo-product-visual" :class="`tone-${artKind}`">
    <img
      v-if="product.mainImage && !imageFailed"
      :src="product.mainImage"
      :alt="product.name"
      loading="lazy"
      @error="imageFailed = true"
    >
    <svg v-else viewBox="0 0 600 520" role="img" :aria-label="product.name">
      <path class="visual-grid" d="M40 90H560M40 430H560M90 40V480M510 40V480" />
      <circle class="visual-orbit" cx="470" cy="104" r="56" />
      <path class="visual-spark" d="M103 93l8 20 20 8-20 8-8 20-8-20-20-8 20-8z" />
      <text class="visual-caption" x="42" y="474">STELLAR / OBJECT {{ String(product.id).padStart(2, '0') }}</text>

      <g v-if="artKind === 'phone'" transform="rotate(-10 300 260)">
        <rect x="215" y="66" width="178" height="356" rx="34" fill="#16191f" />
        <rect x="227" y="79" width="154" height="326" rx="25" fill="#3155ef" />
        <path d="M227 294L381 154V405H227Z" fill="#6f8cff" opacity=".65" />
        <circle cx="253" cy="108" r="13" fill="#16191f" />
        <circle cx="282" cy="108" r="13" fill="#16191f" />
        <circle cx="253" cy="108" r="6" fill="#a9c3ff" />
        <circle cx="282" cy="108" r="6" fill="#a9c3ff" />
        <text x="304" y="245" text-anchor="middle" fill="white" font-size="40" font-weight="700">X100</text>
        <text x="304" y="272" text-anchor="middle" fill="white" font-size="12" letter-spacing="4">PRO 5G</text>
        <rect x="277" y="390" width="54" height="4" rx="2" fill="white" opacity=".8" />
      </g>

      <g v-else-if="artKind === 'laptop'">
        <rect x="125" y="95" width="350" height="250" rx="18" fill="#20242b" />
        <rect x="139" y="110" width="322" height="216" rx="8" fill="#3155ef" />
        <path d="M139 278L350 110H461V326H139Z" fill="#7894ff" opacity=".65" />
        <circle cx="300" cy="218" r="50" fill="none" stroke="white" stroke-width="2" opacity=".7" />
        <path d="M270 218h60M300 188v60" stroke="white" stroke-width="2" opacity=".7" />
        <path d="M92 345H508L466 388H134Z" fill="#c4c9d2" stroke="#20242b" stroke-width="5" />
        <path d="M255 348h90l-8 13h-74z" fill="#878e9b" />
        <path d="M136 364h328" stroke="#878e9b" stroke-width="3" stroke-dasharray="10 7" />
      </g>

      <g v-else-if="artKind === 'audio'">
        <rect x="165" y="223" width="270" height="190" rx="82" fill="#f9fafc" stroke="#1b1d22" stroke-width="6" />
        <path d="M166 284h268" stroke="#1b1d22" stroke-width="5" />
        <circle cx="300" cy="342" r="7" fill="#3155ef" />
        <g transform="rotate(-16 214 176)">
          <path d="M204 95c-34 0-52 27-47 59 4 27 23 40 43 36l8 101c2 27 42 25 40-2l-8-153c-2-27-14-41-36-41z" fill="#f9fafc" stroke="#1b1d22" stroke-width="6" />
          <ellipse cx="198" cy="139" rx="26" ry="31" fill="#3155ef" />
        </g>
        <g transform="rotate(16 385 176)">
          <path d="M396 95c34 0 52 27 47 59-4 27-23 40-43 36l-8 101c-2 27-42 25-40-2l8-153c2-27 14-41 36-41z" fill="#f9fafc" stroke="#1b1d22" stroke-width="6" />
          <ellipse cx="402" cy="139" rx="26" ry="31" fill="#3155ef" />
        </g>
      </g>

      <g v-else-if="artKind === 'keyboard'" transform="rotate(-7 300 270)">
        <rect x="77" y="143" width="446" height="257" rx="25" fill="#242831" />
        <rect x="94" y="161" width="412" height="204" rx="12" fill="#e8ecf2" />
        <g fill="#ffffff" stroke="#242831" stroke-width="2">
          <path d="M111 180h365v28H111zM111 219h365v28H111zM111 258h365v28H111zM111 297h365v28H111zM111 336h365v12H111z" />
          <path d="M154 180v168M198 180v168M242 180v168M286 180v168M330 180v168M374 180v168M418 180v168M462 180v168" fill="none" />
        </g>
        <rect x="242" y="297" width="132" height="28" fill="#3155ef" stroke="#242831" stroke-width="2" />
        <circle cx="479" cy="383" r="5" fill="#d7ff4f" />
      </g>

      <g v-else-if="artKind === 'tv'">
        <rect x="70" y="91" width="460" height="286" rx="12" fill="#171b23" />
        <rect x="83" y="105" width="434" height="254" rx="3" fill="#3155ef" />
        <path d="M83 282L332 105H517V359H83Z" fill="#7894ff" opacity=".6" />
        <circle cx="402" cy="184" r="82" fill="#d7ff4f" opacity=".85" />
        <path d="M279 377h42l-9 42h55v13H233v-13h55zM403 377h-42l9 42h-55v13h134v-13h-55z" fill="#171b23" />
        <text x="111" y="331" fill="white" font-size="16" letter-spacing="5">QLED 4K</text>
      </g>

      <g v-else>
        <rect x="96" y="148" width="408" height="182" rx="45" fill="#f8fafc" stroke="#20242b" stroke-width="6" />
        <path d="M123 269h354" stroke="#20242b" stroke-width="5" />
        <path d="M142 286h316" stroke="#939ba9" stroke-width="4" stroke-dasharray="5 8" />
        <circle cx="449" cy="198" r="9" fill="#3155ef" />
        <path d="M205 361c-31 26-31 52 0 78M300 361c-31 26-31 52 0 78M395 361c-31 26-31 52 0 78" fill="none" stroke="#3155ef" stroke-width="7" stroke-linecap="round" />
        <text x="145" y="211" fill="#20242b" font-size="26" font-weight="700">26.0°</text>
      </g>
    </svg>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { Spu } from '@/types/models'

const props = defineProps<{ product: Spu }>()
const imageFailed = ref(false)
watch(() => props.product.mainImage, () => { imageFailed.value = false })

const artKind = computed(() => {
  const text = `${props.product.name} ${props.product.subTitle || ''}`
  if (/手机|Phone/i.test(text)) return 'phone'
  if (/笔记本|电脑办公|Laptop/i.test(text)) return 'laptop'
  if (/耳机|Pods|降噪/i.test(text)) return 'audio'
  if (/键盘/i.test(text)) return 'keyboard'
  if (/电视/i.test(text)) return 'tv'
  return 'appliance'
})
</script>

<style scoped>
.demo-product-visual {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: #e4e9f5;
}
.demo-product-visual.tone-laptop { background: #e9e5dc; }
.demo-product-visual.tone-audio { background: #e4eee9; }
.demo-product-visual.tone-keyboard { background: #eee4da; }
.demo-product-visual.tone-tv { background: #e8e4ee; }
.demo-product-visual.tone-appliance { background: #e3edf0; }
img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
svg { display: block; width: 100%; height: 100%; }
.visual-grid {
  fill: none;
  stroke: currentColor;
  stroke-width: 1;
  opacity: .1;
  stroke-dasharray: 3 8;
}
.visual-orbit { fill: none; stroke: #3155ef; stroke-width: 2; stroke-dasharray: 4 7; }
.visual-spark { fill: #d7ff4f; stroke: #171b23; stroke-width: 2; }
.visual-caption {
  fill: currentColor;
  font-family: 'Courier New', monospace;
  font-size: 12px;
  letter-spacing: 2px;
  opacity: .62;
}
</style>
