(ns label-printer.core
  (:require [byte-streams :refer [to-byte-array]]
            [clojure.java.io :as io])
  (:gen-class)
  (:import (org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream$AppendMode PDPageContentStream)
           (org.apache.pdfbox.pdmodel.graphics.image PDImageXObject)
           (org.apache.pdfbox.pdmodel.common PDRectangle)
           (com.google.zxing.qrcode QRCodeWriter)
           (com.google.zxing BarcodeFormat EncodeHintType)
           (com.google.zxing.client.j2se MatrixToImageWriter)
           (com.google.zxing.pdf417.decoder.ec ErrorCorrection)
           (com.google.zxing.qrcode.decoder ErrorCorrectionLevel)
           (java.io ByteArrayOutputStream)
           (javax.imageio ImageIO)
           (java.awt.image BufferedImage)
           (org.apache.pdfbox.pdmodel.font PDType1Font)
           (org.apache.pdfbox.pdmodel.graphics.color PDColor)
           (java.awt Color)))


(defn rand-str [len]
  (let [chars (seq "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890")]
    (apply str (take len (repeatedly #(rand-nth chars))))))

(defn inches->pu [i]
  (* i 72))

(defn resource->byte-array [resource]
  (let [input (io/input-stream resource)
        out (to-byte-array input)
        _ (.close input)]
    out))

(def L7163
  {:h-pitch     4
   :v-pitch     1.5
   :width       3.9
   :heigth      1.5
   :left-margin 0.18
   :top-margin  0.6
   :columns     2
   :rows        7})

(defn get-labels [{:keys [h-pitch v-pitch width height
                          left-margin top-margin
                          columns rows] :as template}]
  (let [n (* columns rows)]
    (loop [i 0
           x left-margin
           y top-margin
           r 0
           c 0
           result []]
      (cond
        (< c columns)
          (recur (inc i) (+ x h-pitch) y r (inc c)
                 (conj result [x y]))
        (< (inc r) rows)
          (recur (inc i) left-margin (+ y v-pitch) (inc r) 0 result)
        :else result)
      )))

(defn get-scale-factor [^PDImageXObject image max-width max-height]
  (let [w (.getWidth image)
        h (.getHeight image)]
    (if (> w h)
      (/ (float max-width) w)
      (/ (float max-height) h))))

(defn draw-image! [^PDPageContentStream content ^PDImageXObject image x y width height]
  (let [sf (get-scale-factor image (inches->pu width)
                             (inches->pu height))
        w (.getWidth image)
        h (.getHeight image)]
    (.drawImage content image (float (inches->pu x)) (float (inches->pu y))
                (float (* w sf)) (float (* h sf)))))

(defn generate-qr-code [url]
  (let [writer (QRCodeWriter.)
        matrix (.encode writer url BarcodeFormat/QR_CODE 1000 1000
                        {EncodeHintType/MARGIN           2
                         EncodeHintType/ERROR_CORRECTION ErrorCorrectionLevel/L})
        image-buffer (MatrixToImageWriter/toBufferedImage matrix)]
    image-buffer))

(defn buffered-image->byte-array [^BufferedImage image]
  (with-open [out (ByteArrayOutputStream.)]
    (ImageIO/write image "png" out)
    (.flush out)
    (.toByteArray out)))

(defn random-qr-code-image [^PDDocument doc]
  (PDImageXObject/createFromByteArray
    doc
    (buffered-image->byte-array
      (generate-qr-code (str "senzd.nl/" (rand-str 6)))) "code"))

(def layouts
  { "kadaster" {:name "Kadaster"
                :background (io/resource "L7163-background-kadaster.png")
                :font-color (Color/WHITE)
                :top-offset 0.35}
    "RWS" {:name "Rijkswaterstaat"
           :background (io/resource "L7163-background-RWS.png")
           :font-color (Color/BLACK)
           :top-offset 1.0   }
   "nijmegen" {:name "Nijmegen"
          :background (io/resource "L7163-background-nijmegen.png")
          :font-color (Color/BLACK)
          :top-offset 0.50   }
   "eindhoven" {:name "Eindhoven"
               :background (io/resource "L7163-background-eindhoven.png")
               :font-color (Color/BLACK)
               :top-offset 0.50   }
   "amsterdam" {:name "Amsterdam"
                :background (io/resource "L7163-background-amsterdam.png")
                :font-color (Color/WHITE)
                :top-offset 0.35   }})


(defn add-label-page! [doc layout]
  (let [size-x 3.9
        size-y 1.5
        text-y (- size-y (:top-offset layout))
        image-buffer (resource->byte-array (:background layout))
        page (PDPage. PDRectangle/A4)
        _ (doto doc (.addPage page))
        image (PDImageXObject/createFromByteArray doc image-buffer "bg")]
    (with-open [^PDPageContentStream content (PDPageContentStream. doc
                                                                   page PDPageContentStream$AppendMode/OVERWRITE
                                                                   true true)]
      (doseq [[x y] (get-labels L7163)]
        (draw-image! content image x y size-x size-y)
        (draw-image! content (random-qr-code-image doc) (+ x 2.55) (+ y 0.15) 1.2 1.2)
        (doto content
          (.setNonStrokingColor ^Color (:font-color layout))
          (.beginText)
          (.setFont PDType1Font/HELVETICA (inches->pu 0.21))
          (.newLineAtOffset (inches->pu (+ x 0.15)) (inches->pu (+ y text-y)))
          (.showText "Wat meet deze sensor?")
          (.endText)

          (.beginText)
          (.setFont PDType1Font/HELVETICA (inches->pu 0.12))
          (.newLineAtOffset (inches->pu (+ x 0.15)) (inches->pu (+ y (- text-y 0.2))))
          (.showText "Scan QR code voor meer informatie")
          (.endText)

          ;(.beginText)
          ;(.setFont PDType1Font/HELVETICA (inches->pu 0.09))
          ;(.newLineAtOffset (inches->pu (+ x 0.15)) (inches->pu (+ y 0.1)))
          ;(.showText (str "Powered by " (:name layout)))
          ;(.endText)

          (.saveGraphicsState)
          )))))


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (doseq [[layout-key layout] layouts]
    (with-open [doc (PDDocument.)]
      (add-label-page! doc layout)
      (.save doc (format "target/test-pdf-%s.pdf", layout-key))))
  (with-open [doc (PDDocument.)]
    (doseq [[layout-key layout] layouts]
      (add-label-page! doc layout))
    (.save doc "target/test-pdf-all.pdf")))
