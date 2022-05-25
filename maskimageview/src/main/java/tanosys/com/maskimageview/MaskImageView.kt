package tanosys.com.maskimageview

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Pair
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

public class MaskImageView : View {
    companion object {
        private val DEFAULT_LINE_WIDTH = 20.0f
        var MAX_SIDE_SIZE = 1024
    }

    private var _paint = Paint()
    private var _bitmapPaint = Paint()
    var _originalBitmapPaint = Paint()
    private var _path: Path = Path()
    private var _x: Float = 0f
    private var _y: Float = 0f
    private var _originalBitmap: Bitmap? = null
    private var _bitmap: Bitmap? = null
    private var _canvas: Canvas? = null
    private var _translation: PointF = PointF(0f, 0f)
    private var _lastDirection: Pair<Direction, Direction> = Pair(Direction.None, Direction.None)
    private var _scale = 1.0f
        set(value) {
            val defaultTranslation = defaultTranslation()
            val translationDistance = PointF((_translation.x - defaultTranslation.x) / _scale, (_translation.y - defaultTranslation.y) / _scale)
            field = Math.min(5.0f,value)
            val maxTranslation = maxTranslation()
            val newTranslation = defaultTranslation()
            newTranslation.x += (translationDistance.x * _scale)
            newTranslation.y += (translationDistance.y * _scale)
            if (newTranslation.x < 0) {
                _translation.x = Math.max(maxTranslation.x, newTranslation.x)
            } else {
                _translation.x = Math.min(0f, newTranslation.x)
            }
            if (newTranslation.y < 0) {
                _translation.y = Math.max(maxTranslation.y, newTranslation.y)
            } else {
                _translation.y = Math.min(0f, newTranslation.y)
            }
            _paint.strokeWidth = lineWidth / value
        }
    var lineWidth: Float = DEFAULT_LINE_WIDTH
        set(value) {
            field = value
            _paint.strokeWidth = lineWidth / _scale
        }
    var maskType: MaskType = MaskType.Mask
        set(value) {
            when (value) {
                MaskType.Mask -> {
                    _paint.color = Color.WHITE
                    _paint.xfermode = null
                }
                MaskType.Recover -> {
                    _paint.color = Color.TRANSPARENT
                    _paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                }
            }
            _scaleGestureListener.isScaling = false
            field = value
        }
    private lateinit var _scaleGestureDetector: ScaleGestureDetector

    var maxSideWidth: Int = MAX_SIDE_SIZE

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init(context)
    }

    private fun init(context: Context) {
        _scaleGestureDetector = ScaleGestureDetector(context, _scaleGestureListener)
        _bitmapPaint = Paint(Paint.DITHER_FLAG)
        _bitmapPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        _originalBitmapPaint = Paint()
        _originalBitmapPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OUT)
        _paint.isAntiAlias = true
        _paint.isDither = true
        _paint.color = Color.WHITE
        _paint.style = Paint.Style.STROKE
        _paint.strokeJoin = Paint.Join.ROUND
        _paint.strokeCap = Paint.Cap.ROUND
        _paint.strokeWidth = MaskImageView.DEFAULT_LINE_WIDTH
        setLayerType(LAYER_TYPE_HARDWARE, null)
        maskType = MaskType.Mask
        lineWidth = DEFAULT_LINE_WIDTH
    }

    fun isPrepared(): Boolean {
        return _originalBitmap != null && _bitmap != null
    }

    fun prepare(original: Bitmap, mask: Bitmap) {
        _scale = 1.0f
        val nWidth: Int
        val nHeight: Int
        if (this.width * original.height <= this.height * original.width) {
            val ratio = original.height.toDouble() / original.width.toDouble()
            nWidth = this.width
            nHeight = (this.width.toDouble() * ratio).toInt()
        } else {
            val ratio = original.width.toDouble() / original.height.toDouble()
            nWidth = (this.height.toDouble() * ratio).toInt()
            nHeight = this.height
        }
        _originalBitmap = Bitmap.createScaledBitmap(original, nWidth, nHeight, true)
        val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val scaledMask = Bitmap.createScaledBitmap(mask, nWidth, nHeight, true)
        val canvas = Canvas(maskBitmap)
        canvas.drawBitmap(scaledMask, (this.width - scaledMask.width).toFloat() / 2.0f
                , (this.height - scaledMask.height).toFloat() / 2.0f, null)
        _bitmap = maskBitmap
        _canvas = Canvas(_bitmap)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (_originalBitmap != null && _bitmap != null) {
            val originalBitmap = _originalBitmap!!
            val bitmap = _bitmap!!
            prepare(originalBitmap, bitmap)
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.drawColor(Color.TRANSPARENT)
        if (_originalBitmap != null && _bitmap != null) {
            canvas.translate(_translation.x, _translation.y)
            canvas.scale(_scale, _scale)
            val originalBitmap = _originalBitmap!!
            val bitmap = _bitmap!!
            canvas.drawBitmap(originalBitmap, (this.width - originalBitmap.width).toFloat() / 2.0f
                    , (this.height - originalBitmap.height).toFloat() / 2.0f, _originalBitmapPaint)
            canvas.drawBitmap(bitmap, 0f, 0f, _bitmapPaint)
            _canvas?.drawPath(_path, _paint)
        }
        canvas.restore()
    }

    private fun touchStart(x: Float, y: Float) {
        when (maskType) {
            MaskType.Mask, MaskType.Recover -> {
                touchStartForDraw(x, y)
            }
            MaskType.Scale -> {
                touchStartForTranslate(x, y)
            }
        }
    }

    private fun touchStartForDraw(x: Float, y: Float) {
        _path.reset()
        _path.moveTo(x, y)
        _x = x
        _y = y
    }

    private fun touchStartForTranslate(x: Float, y: Float) {
        _x = x
        _y = y
    }

    private fun touchMove(x: Float, y: Float) {
        when (maskType) {
            MaskType.Mask, MaskType.Recover -> {
                touchMoveForDraw(x, y)
            }
            MaskType.Scale -> {
                touchMoveForTranslate(x, y)
            }
        }
    }

    private fun touchMoveForDraw(x: Float, y: Float) {
        val dx = Math.abs(x - _x)
        val dy = Math.abs(y - _y)
        if (dx >= 4.0 || dy >= 4.0) {
            _path.quadTo(_x, _y, (x + _x) / 2, (y + _y) / 2)
            _x = x
            _y = y
        }
    }

    private fun touchMoveForTranslate(x: Float, y: Float) {
        val direction = getMoveDirection(PointF(_x, _y), PointF(x, y))
        val newTranslation = PointF(_translation.x, _translation.y)
        val maxTranslation = maxTranslation()
        if (_lastDirection.first == direction.first) {
            val disX = x - _x
            newTranslation.x += disX
        }
        if (_lastDirection.second == direction.second) {
            val disY = y - _y
            newTranslation.y += disY
        }
        if (newTranslation.x < 0) {
            _translation.x = Math.max(maxTranslation.x, newTranslation.x)
        } else {
            _translation.x = Math.min(0f, newTranslation.x)
        }

        if (newTranslation.y < 0) {
            _translation.y = Math.max(maxTranslation.y, newTranslation.y)
        } else {
            _translation.y = Math.min(0f, newTranslation.y)
        }
        _x = x
        _y = y
        _lastDirection = direction
    }

    private fun touchUp() {
        when (maskType) {
            MaskType.Mask, MaskType.Recover -> {
                touchUpForDraw(x, y)
            }
            MaskType.Scale -> {
                touchUpForTranslate(x, y)
            }
        }
    }

    private fun touchUpForDraw(x: Float, y: Float) {
        _path.lineTo(_x, _y)
        _canvas?.drawPath(_path, _paint)
        _path.reset()
    }

    private fun touchUpForTranslate(x: Float, y: Float) {

    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (_originalBitmap == null || _bitmap == null) {
            return true
        }
        event?.let { event ->
            if (maskType == MaskType.Scale) {
                _scaleGestureDetector.onTouchEvent(event)
                if (_scaleGestureListener.isScaling) {
                    _scaleGestureListener.isScaling = event.action != MotionEvent.ACTION_UP
                    return true
                }
            }

            val x: Float
            val y: Float
            when (maskType) {
                MaskType.Mask, MaskType.Recover -> {
                    val pointInCanvas = getPointInCanvas(event)
                    x = pointInCanvas.x
                    y = pointInCanvas.y
                }
                MaskType.Scale -> {
                    x = event.x
                    y = event.y
                }
            }

            when (event.getAction()) {
                MotionEvent.ACTION_DOWN -> {
                    touchStart(x, y)
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    touchMove(x, y)
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touchUp()
                    invalidate()
                }
            }
        }
        return true
    }

    private fun getPointInCanvas(event: MotionEvent): PointF {
        return PointF((event.x - _translation.x) / _scale, (event.y - _translation.y) / _scale)
    }

    private fun defaultTranslation(): PointF {
        return PointF(-((_scale - 1.0f) * this.width) / 2.0f, -((_scale - 1.0f) * this.height) / 2.0f)
    }

    private fun maxTranslation(): PointF {
        return PointF(-((_scale - 1.0f) * this.width), -((_scale - 1.0f) * this.height))
    }

    private fun getMoveDirection(lastPoint: PointF, currentPoint: PointF): Pair<Direction, Direction> {
        val horizontalDirection: Direction
        val verticalDirection: Direction
        if (lastPoint.x > currentPoint.x) {
            horizontalDirection = Direction.Left
        } else if (lastPoint.x < currentPoint.x) {
            horizontalDirection = Direction.Right
        } else {
            horizontalDirection = Direction.None
        }
        if (lastPoint.y > currentPoint.y) {
            verticalDirection = Direction.Top
        } else if (lastPoint.y < currentPoint.y) {
            verticalDirection = Direction.Bottom
        } else {
            verticalDirection = Direction.None
        }
        return Pair(horizontalDirection, verticalDirection)
    }

    private val _scaleGestureListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        var isScaling: Boolean = false

        override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
            isScaling = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector?): Boolean {
            detector?.let {
                _scale = Math.max(1.0f, _scale * it.scaleFactor)
                invalidate()
            }
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector?) {
        }

    }

    fun createMaskedImage(): Bitmap? {
        val bitmap: Bitmap
        this._scale = 1.0f
        invalidate()
        this.isDrawingCacheEnabled = true
        bitmap = Bitmap.createBitmap(this.drawingCache)
        this.isDrawingCacheEnabled = false
        return createClippedImage(bitmap)?.let { bitmap ->
            if (bitmap.width > maxSideWidth) {
                val dstWidth: Int
                val dstHeight: Int
                if (bitmap.width > bitmap.height) {
                    val ratio = bitmap.height.toDouble() / bitmap.width.toDouble()
                    dstWidth = maxSideWidth
                    dstHeight = (dstWidth.toDouble() * ratio).toInt()
                } else {
                    val ratio = bitmap.width.toDouble() / bitmap.height.toDouble()
                    dstHeight = maxSideWidth
                    dstWidth = (dstHeight.toDouble() * ratio).toInt()
                }
                return@let Bitmap.createScaledBitmap(bitmap, dstWidth, dstHeight, true)
            }
            return@let bitmap
        } ?: null
    }

    fun createClippedImage(bitmap: Bitmap): Bitmap? {
        val width = bitmap.width;
        val height = bitmap.height;
        var pixels = IntArray(width * height)
        var leftEdge = width
        var rightEdge = 0
        var topEdge = -1
        var bottomEdge = 0
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (y in 0 until height) {
            var leftEdgeForRow = -1
            var rightEdgeForRow = 0
            var isTransParentRow = true
            for (x in 0 until width) {
                val pixel = pixels[x + y * width];
                val alpha = Color.alpha(pixel)
                val isTransparent = alpha == 0
                if (!isTransparent) {
                    isTransParentRow = false
                    if (leftEdgeForRow < 0) {
                        leftEdgeForRow = x
                    }
                    rightEdgeForRow = x
                }
            }
            if (leftEdgeForRow < leftEdge && leftEdgeForRow >= 0) {
                leftEdge = leftEdgeForRow
            }
            if (rightEdgeForRow > rightEdge) {
                rightEdge = rightEdgeForRow
            }
            if (!isTransParentRow) {
                if (topEdge < 0) {
                    topEdge = y
                }
                bottomEdge = y
            }
        }
        if (topEdge < 0)
            topEdge = 0
        val dstWidth = rightEdge - leftEdge
        val dstHeight = bottomEdge - topEdge
        return if (dstWidth <= 0 || dstHeight <= 0) null else Bitmap.createBitmap(bitmap, leftEdge, topEdge, dstWidth, dstHeight)
    }

    enum class MaskType {
        Mask,
        Recover,
        Scale
    }

    enum class Direction {
        Top,
        Left,
        Bottom,
        Right,
        None
    }

}