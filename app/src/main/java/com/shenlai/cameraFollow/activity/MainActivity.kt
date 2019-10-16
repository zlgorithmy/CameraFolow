package com.shenlai.cameraFollow.activity

import android.app.Activity
import android.graphics.Point
import android.graphics.Rect
import android.hardware.Camera
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import com.arcsoft.face.*
import com.friendlyarm.FriendlyThings.HardwareControler
import com.shenlai.cameraFollow.R
import com.shenlai.cameraFollow.common.Constants
import com.shenlai.cameraFollow.model.DrawInfo
import com.shenlai.cameraFollow.util.ConfigUtil
import com.shenlai.cameraFollow.util.DrawHelper
import com.shenlai.cameraFollow.util.HexTest
import com.shenlai.cameraFollow.util.camera.CameraHelper
import com.shenlai.cameraFollow.util.camera.CameraListener
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.toast
import java.util.*
import kotlin.math.abs

@ExperimentalStdlibApi
class MainActivity : Activity() {
    private val debug: Boolean by lazy {
        true
    }

    private fun log(log: String) {
        if (debug) Log.i("MainActivity", log)
    }

    private lateinit var cameraHelper: CameraHelper
    private lateinit var frontDrawHelper: DrawHelper
    private lateinit var backDrawHelper: DrawHelper
    private lateinit var frontPreviewSize: Camera.Size
    private lateinit var backPreviewSize: Camera.Size
    private val frontCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT //1
    private val backCameraId = Camera.CameraInfo.CAMERA_FACING_BACK //0
    private var afCode = -1
    private val processMask =
        FaceEngine.ASF_AGE or
                FaceEngine.ASF_FACE3DANGLE or
                FaceEngine.ASF_GENDER or
                FaceEngine.ASF_LIVENESS
    private val initMask =
        FaceEngine.ASF_FACE_DETECT or processMask
    private val detectFaceScaleVal = 32 //2-32(default 16)
    private val detectFaceMaxNum = 1 //0-50
    private val faceEngine: FaceEngine by lazy {
        FaceEngine()
    }

    private val timer = Timer()
    private val MAXLINES = 200
    private val BUFSIZE = 512

    // NanoPC-T4 UART4
    private val devName = "/dev/ttyS4"
    private val speed: Long = 9600
    private val dataBits = 8
    private val stopBits = 1
    private var devfd = -1
    private val readBuf = ByteArray(BUFSIZE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.attributes.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE
        setContentView(R.layout.activity_main)

        if (ConfigUtil.firstUse(baseContext)) {
            activeEngine()
        }
        if (ConfigUtil.getActived(baseContext) == false) {
            activeEngine()
        }
        initHardWare()
        initEngine()
        mCameraFrontPreview.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val cameraListener = object : CameraListener {
                    override fun onCameraOpened(camera: Camera, cameraId: Int, displayOrientation: Int, isMirror: Boolean) {
                        log("onCameraOpened: $cameraId  $displayOrientation $isMirror")
                        frontPreviewSize = camera.parameters.previewSize
                        frontDrawHelper = DrawHelper(
                            frontPreviewSize.width,
                            frontPreviewSize.height,
                            mCameraFrontPreview.width,
                            mCameraFrontPreview.height,
                            displayOrientation,
                            cameraId,
                            isMirror,
                            false,
                            false
                        )
                    }


                    override fun onPreview(nv21: ByteArray, camera: Camera) {
                        mCameraFrontBytes = nv21
                    }

                    override fun onCameraClosed() {
                        log("onCameraClosed: ")
                    }

                    override fun onCameraError(e: Exception) {
                        log("onCameraError: " + e.message)
                    }

                    override fun onCameraConfigurationChanged(cameraID: Int, displayOrientation: Int) {
                        frontDrawHelper.cameraDisplayOrientation = displayOrientation
                        log("onCameraConfigurationChanged: $cameraID  $displayOrientation")
                    }
                }
                val metrics = DisplayMetrics()
                windowManager.defaultDisplay.getMetrics(metrics)


                cameraHelper = CameraHelper.Builder()
                    .previewViewSize(Point(mCameraFrontPreview.measuredWidth, mCameraFrontPreview.measuredHeight))
                    .rotation(windowManager.defaultDisplay.rotation)
                    .specificCameraId(frontCameraId)
                    .isMirror(true)
                    .previewOn(mCameraFrontPreview)
                    .cameraListener(cameraListener)
                    .previewSize(Point(1280, 720))
                    .build()
                cameraHelper.init()
                cameraHelper.start()
                mCameraFrontPreview.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
        mCameraBackPreview.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val cameraListener = object : CameraListener {
                    override fun onCameraOpened(camera: Camera, cameraId: Int, displayOrientation: Int, isMirror: Boolean) {
                        if (devfd != -1) {
                            //timer.schedule(mBackFaceDetectTask, 0, 20)
                            //faceDetectThread.start()
                        }
                        log("onCameraOpened: $cameraId  $displayOrientation $isMirror")
                        backPreviewSize = camera.parameters.previewSize
                        backDrawHelper = DrawHelper(
                            backPreviewSize.width,
                            backPreviewSize.height,
                            mCameraBackPreview.width,
                            mCameraBackPreview.height,
                            displayOrientation,
                            cameraId,
                            isMirror,
                            false,
                            false
                        )
                    }


                    override fun onPreview(nv21: ByteArray, camera: Camera) {
                        //if (mLastFaceTime>1000) {
                        mCameraBackBytes = nv21
                        //}
                    }

                    override fun onCameraClosed() {
                        log("onCameraClosed: ")
                    }

                    override fun onCameraError(e: Exception) {
                        log("onCameraError: " + e.message)
                    }

                    override fun onCameraConfigurationChanged(cameraID: Int, displayOrientation: Int) {
                        backDrawHelper.cameraDisplayOrientation = displayOrientation
                        log("onCameraConfigurationChanged: $cameraID  $displayOrientation")
                    }
                }
                val metrics = DisplayMetrics()
                windowManager.defaultDisplay.getMetrics(metrics)


                cameraHelper = CameraHelper.Builder()
                    .previewViewSize(Point(mCameraBackPreview.measuredWidth, mCameraBackPreview.measuredHeight))
                    .rotation(windowManager.defaultDisplay.rotation)
                    .specificCameraId(backCameraId)
                    .isMirror(false)
                    .previewOn(mCameraBackPreview)
                    .cameraListener(cameraListener)
                    .previewSize(Point(640, 480))
                    .build()
                cameraHelper.init()
                cameraHelper.start()
                mCameraBackPreview.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }

    private var readyToSend = false
    private var readThread = Thread(Runnable {
        while (true) {
            if (HardwareControler.select(devfd, 0, 0) != 1) {
                continue
            }
            val retSize = HardwareControler.read(devfd, readBuf, BUFSIZE)
            if (retSize > 0) {
                readyToSend = true
                log("hardware ${sendCnt--} recv:${HexTest.byteArrToHex(readBuf.copyOfRange(0, retSize))}")
            }

        }
    })

    private fun waitToSend(usec: Long = 10) {
        while (!readyToSend) Thread.sleep(usec)
    }

    private var initHardware: Boolean = false
    private fun resetMotor() {
        synchronized(this) {
            initHardware = false
            if (devfd >= 0) {
                Thread(Runnable {
                    waitToSend()
                    send(byteArrayOf(0xA5.toByte(), getByte(0, true), getByte(1, false), 0x00.toByte()))
                    waitToSend()
                    send(byteArrayOf(0xA5.toByte(), getByte(0, true), getByte(1, true), 0x00.toByte()))
                    waitToSend()
                    send(byteArrayOf(0xA5.toByte(), getByte(1, false), getByte(0, true), 0x00.toByte()))
                    waitToSend()
                    send(byteArrayOf(0xA5.toByte(), getByte(1, true), getByte(0, true), 0x00.toByte()))
                    waitToSend()
                    initHardware = true
                    tx = 0
                    ty = 0
                }).start()
            }
        }
    }

    private fun initHardWare() {
        devfd = HardwareControler.openSerialPort(devName, speed, dataBits, stopBits)
        if (devfd >= 0) {
            log("init hardware success")
            readyToSend = true
            readThread.start()
            resetMotor()
        } else {
            devfd = -1
            toast("init hardware failed.")
            log("init hardware failed")
        }
    }

    private var mLastFaceTime = System.currentTimeMillis()
    private var mReset = true
    private fun faceDetect() {
        if (mCameraBackBytes == null || !initHardware || !readyToSend) return
        if (mFaceRectView != null) {
            mFaceRectView.clearFaceInfo()
        }
        if (mFaceRectView1 != null) {
            mFaceRectView1.clearFaceInfo()
        }
        val t = System.currentTimeMillis()
        val faceInfoList = ArrayList<FaceInfo>()
        var code = faceEngine.detectFaces(mCameraBackBytes, backPreviewSize.width, backPreviewSize.height, FaceEngine.CP_PAF_NV21, faceInfoList)
        if (code == ErrorInfo.MOK && faceInfoList.size > 0) {
            //log("front  camera onPreview: " + faceInfoList.size + " (" + frontPreviewSize.width + "," + frontPreviewSize.height + ")")
            code = faceEngine.process(
                mCameraBackBytes,
                backPreviewSize.width,
                backPreviewSize.height,
                FaceEngine.CP_PAF_NV21,
                faceInfoList,
                processMask
            )
            if (code != ErrorInfo.MOK) {
                return
            }
        } else {
            var dt = t - mLastFaceTime
            if (dt > 10000 && !mReset) {
                mReset = true
                resetMotor()
                mLastFaceTime = t
            }
            return
        }

        val ageInfoList = ArrayList<AgeInfo>()
        val genderInfoList = ArrayList<GenderInfo>()
        val face3DAngleList = ArrayList<Face3DAngle>()
        val faceLivenessInfoList = ArrayList<LivenessInfo>()
        val ageCode = faceEngine.getAge(ageInfoList)
        val genderCode = faceEngine.getGender(genderInfoList)
        val face3DAngleCode = faceEngine.getFace3DAngle(face3DAngleList)
        val livenessCode = faceEngine.getLiveness(faceLivenessInfoList)

        //有其中一个的错误码不为0，return
        if (ageCode or genderCode or face3DAngleCode or livenessCode != ErrorInfo.MOK) {
            return
        }

        if (mFaceRectView != null) {
            val drawInfoList = ArrayList<DrawInfo>()
            for (i in faceInfoList.indices) {
                drawInfoList.add(
                    DrawInfo(
                        backDrawHelper.adjustRect(faceInfoList[i].rect),
                        genderInfoList[i].gender,
                        ageInfoList[i].age,
                        faceLivenessInfoList[i].liveness, null
                    )
                )
            }
            backDrawHelper.draw(mFaceRectView, drawInfoList)
            val rect = faceInfoList[0].rect
            val xy = getPosition(rect, backPreviewSize)
            if (rect.centerX() in 536..544 && rect.centerY() in 950..970) {
                toast("center")
            }
            if (xy[0] == 0 && xy[1] == 0) {
                return
            }
            send(
                byteArrayOf(
                    0xA5.toByte(),
                    getByte(xy[0], forwardX),
                    getByte(xy[1], forwardY),
                    0x00.toByte()
                )
            )
            mReset = false
            mLastFaceTime = t
        }
    }

    private var xScale = 40
    private var yScale = 30
    private fun getPosition(rect: Rect, size: Camera.Size): Array<Int> {
        val x = (rect.centerX() - size.width / 2) * xScale / size.width //目标位置x
        val y = (rect.centerY() - size.height / 2) * yScale / size.height //目标位置y
        var cx = x - tx //需要移动的距离x
        var cy = y - ty //需要移动的距离y
        log("face1:当前位置:($tx,$ty) 目标位置($x,$y) 需要移动:($cx,$cy) center:(${rect.centerX()},${rect.centerY()}) size:(${size.width / 2},${size.height / 2})")

        tx = x
        ty = y

        forwardX = cx < 0
        forwardY = cy > 0

        if (cx < 0) {
            cx = -cx
        }
        if (cy < 0) {
            cy = -cy
        }
        return arrayOf(cx, cy)
    }

    private var mCameraBackBytes: ByteArray? = null
    private var mCameraFrontBytes: ByteArray? = null


    private fun initEngine() {
        ConfigUtil.setFtOrient(baseContext, FaceEngine.ASF_OP_0_HIGHER_EXT)
        afCode = faceEngine.init(
            this,
            FaceEngine.ASF_DETECT_MODE_VIDEO,
            ConfigUtil.getFtOrient(this),
            detectFaceScaleVal,
            detectFaceMaxNum,
            initMask
        )
        val versionInfo = VersionInfo()
        faceEngine.getVersion(versionInfo)
        log("initEngine:  init: $afCode  version:$versionInfo")
        if (afCode != ErrorInfo.MOK) {
            toast(getString(R.string.init_failed, afCode))
        }
    }

    private fun unInitEngine() {

        if (afCode == 0) {
            afCode = faceEngine.unInit()
            log("unInitEngine: $afCode")
        }
    }


    override fun onDestroy() {
        cameraHelper.release()
        timer.cancel()
        if (devfd != -1) {
            HardwareControler.close(devfd)
            devfd = -1
        }
        unInitEngine()
        super.onDestroy()
    }

    private fun activeEngine() {
        Observable.create(ObservableOnSubscribe<Int> { emitter ->
            val activeCode = faceEngine.activeOnline(
                baseContext,
                Constants.APP_ID,
                Constants.SDK_KEY
            )
            emitter.onNext(activeCode)
        })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<Int> {
                override fun onSubscribe(d: Disposable) {

                }

                override fun onNext(activeCode: Int) {
                    when (activeCode) {
                        ErrorInfo.MOK -> toast(getString(R.string.active_success))
                        ErrorInfo.MERR_ASF_ALREADY_ACTIVATED -> toast(getString(R.string.already_activated))
                        else -> toast(getString(R.string.active_failed, activeCode))
                    }

                    val activeFileInfo = ActiveFileInfo()
                    val res = faceEngine.getActiveFileInfo(baseContext, activeFileInfo)
                    if (res == ErrorInfo.MOK) {
                        log(activeFileInfo.toString())
                        ConfigUtil.setActived(baseContext, true)
                        ConfigUtil.setFtOrient(baseContext, FaceEngine.ASF_OP_270_ONLY)
                    }
                }

                override fun onError(e: Throwable) {
                    e.printStackTrace()
                }

                override fun onComplete() {

                }
            })
    }

    private fun getByte(step: Int, forward: Boolean): Byte {
        if (step < 0 || step > 520) {
            throw RuntimeException("step must in[0,60]")
        }
        return (if (forward) step else (step - 128)).toByte()
    }

    private var sendCnt = 0
    private var forwardX = false
    private var forwardY = true
    private var tx = 0
    private var ty: Int = 0

    private fun send(cmd: ByteArray) {
        val ret = HardwareControler.write(devfd, cmd)
        if (ret > 0) {
            readyToSend = false
            ++sendCnt
            val s = "hardware $sendCnt send:" + HexTest.byteArrToHex(cmd)
            log(s)
        }
    }

    fun resetMotor(view: View) {
        resetMotor()
        xScale = XScale.text.toString().toInt()
        yScale = YScale.text.toString().toInt()
        xxScale = XXScale.text.toString().toInt()
        yyScale = YYScale.text.toString().toInt()
    }

    fun resetMotor0(view: View) {
        readyToSend = true
        if (tx == 0 && ty == 0) {
            return
        }
        send(
            byteArrayOf(
                0xA5.toByte(),
                getByte(abs(ty), tx > 0),
                getByte(abs(ty), ty < 0),
                0x00.toByte()
            )
        )
        tx = 0
        ty = 0
    }

    private var xxScale = 5
    private var yyScale = 5
    //调整人脸到中间
    private fun fineTuning(rect: Rect, size: Camera.Size): Point {
        val x = (rect.centerX() - size.width / 2) * xxScale / size.width //目标x轴偏移位置
        val y = (rect.centerY() - size.height / 2) * yyScale / size.height //目标y轴偏移位置
        var cx = -x //需要移动的距离x
        var cy = -y //需要移动的距离y
        log(
            "face2:当前绝对位置:($tx,$ty) ($x,$y) 目标位置(0,0) 需要移动:($cx,$cy) center:(${rect.centerX()},${rect.centerY()}) size/2:(${size.width / 2},${size
                .height / 2})"
        )

        tx += x
        ty += y

        forwardX = cx > 0
        forwardY = cy < 0

        if (cx < 0) {
            cx = -cx
        }
        if (cy < 0) {
            cy = -cy
        }
        return Point(cx, cy)
    }

    private var faceDetectThread = Thread(Runnable {
        while (devfd != -1) {
            Thread.sleep(10)
            if (!readyToSend || mCameraFrontBytes == null) {
                continue
            }

            val facesList = ArrayList<FaceInfo>()
            var code = faceEngine.detectFaces(
                mCameraFrontBytes, frontPreviewSize.width, frontPreviewSize.height, FaceEngine.CP_PAF_NV21,
                facesList
            )

            var rect: Rect
            if (code == ErrorInfo.MOK && facesList.isNotEmpty()) {
                code = faceEngine.process(
                    mCameraFrontBytes,
                    frontPreviewSize.width,
                    frontPreviewSize.height,
                    FaceEngine.CP_PAF_NV21,
                    facesList,
                    processMask
                )
                mCameraFrontBytes = null
                if (code != ErrorInfo.MOK || facesList.isEmpty()) {
                    continue
                }
                if (mFaceRectView != null) {
                    mFaceRectView.clearFaceInfo()
                }
                if (mFaceRectView1 != null) {
                    mFaceRectView1.clearFaceInfo()
                }

                val ageInfoList = ArrayList<AgeInfo>()
                val genderInfoList = ArrayList<GenderInfo>()
                val face3DAngleList = ArrayList<Face3DAngle>()
                val faceLivenessInfoList = ArrayList<LivenessInfo>()
                val ageCode = faceEngine.getAge(ageInfoList)
                val genderCode = faceEngine.getGender(genderInfoList)
                val face3DAngleCode = faceEngine.getFace3DAngle(face3DAngleList)
                val livenessCode = faceEngine.getLiveness(faceLivenessInfoList)

                //有其中一个的错误码不为0，return
                if (ageCode or genderCode or face3DAngleCode or livenessCode != ErrorInfo.MOK) {
                    continue
                }

                if (mFaceRectView1 != null) {
                    val drawInfoList = ArrayList<DrawInfo>()
                    for (i in facesList.indices) {
                        drawInfoList.add(
                            DrawInfo(
                                frontDrawHelper.adjustRect(facesList[i].rect),
                                genderInfoList[i].gender,
                                ageInfoList[i].age,
                                faceLivenessInfoList[i].liveness, null
                            )
                        )
                    }
                    frontDrawHelper.draw(mFaceRectView1, drawInfoList)
                }


                //TODO 微调
                rect = facesList.first().rect
                val xy = fineTuning(rect, frontPreviewSize)
                log("微调$xy")
                if (xy.equals(0, 0)) {
                    continue
                }
                send(
                    byteArrayOf(
                        0xA5.toByte(),
                        getByte(xy.x, forwardX),
                        getByte(xy.y, forwardY),
                        0x00.toByte()
                    )
                )
            } else {
                if (mFaceRectView1 != null) {
                    mFaceRectView1.clearFaceInfo()
                }
                //TODO 寻找人脸
                code = faceEngine.detectFaces(
                    mCameraBackBytes, backPreviewSize.width, backPreviewSize.height, FaceEngine.CP_PAF_NV21,
                    facesList
                )
                if (code == ErrorInfo.MOK && facesList.isNotEmpty()) {
                    //TODO 调整
                    code = faceEngine.process(
                        mCameraBackBytes,
                        backPreviewSize.width,
                        backPreviewSize.height,
                        FaceEngine.CP_PAF_NV21,
                        facesList,
                        processMask
                    )
                    mCameraBackBytes = null
                    if (code != ErrorInfo.MOK || facesList.isEmpty()) {
                        continue
                    }
                    val ageInfoList = ArrayList<AgeInfo>()
                    val genderInfoList = ArrayList<GenderInfo>()
                    val face3DAngleList = ArrayList<Face3DAngle>()
                    val faceLivenessInfoList = ArrayList<LivenessInfo>()
                    val ageCode = faceEngine.getAge(ageInfoList)
                    val genderCode = faceEngine.getGender(genderInfoList)
                    val face3DAngleCode = faceEngine.getFace3DAngle(face3DAngleList)
                    val livenessCode = faceEngine.getLiveness(faceLivenessInfoList)

                    //有其中一个的错误码不为0，return
                    if (ageCode or genderCode or face3DAngleCode or livenessCode != ErrorInfo.MOK) {
                        continue
                    }

                    if (mFaceRectView != null) {
                        val drawInfoList = ArrayList<DrawInfo>()
                        for (i in facesList.indices) {
                            drawInfoList.add(
                                DrawInfo(
                                    backDrawHelper.adjustRect(facesList[i].rect),
                                    genderInfoList[i].gender,
                                    ageInfoList[i].age,
                                    faceLivenessInfoList[i].liveness, null
                                )
                            )
                        }
                        backDrawHelper.draw(mFaceRectView, drawInfoList)
                        val rect0 = facesList[0].rect
                        val xy = getPosition(rect0, backPreviewSize)
                        if (xy[0] == 0 && xy[1] == 0) {
                            continue
                        }
                        send(
                            byteArrayOf(
                                0xA5.toByte(),
                                getByte(xy[0], forwardX),
                                getByte(xy[1], forwardY),
                                0x00.toByte()
                            )
                        )
                    }
                } else {
                    //TODO 没检测到人
                    if (mFaceRectView != null) {
                        mFaceRectView.clearFaceInfo()
                    }
                }
            }
        }
    })

    fun start(view: View) {
        if (devfd != -1) {
            //timer.schedule(mBackFaceDetectTask, 0, 20)
            if (!faceDetectThread.isAlive) {
                faceDetectThread.start()
            }
        }
    }
}
