package computing

import java.io.File

import org.bytedeco.javacpp.indexer.UByteRawIndexer
import org.bytedeco.javacpp.opencv_core._
import org.bytedeco.javacpp.opencv_features2d.{BFMatcher, ORB}
import org.bytedeco.javacpp.opencv_imgcodecs.{IMREAD_COLOR, imread}
import utils.Logger

object OpenCVUtils extends Logger {

  def computeDescriptor(file: File): Mat = {
    computeDescriptor(loadOrExit(file))
  }

  // to be safe, I put maxDistance at 1000 * number of matches to select (100 would be enough 99.99% of the time)
  def similarity(descriptor1: Mat, descriptor2: Mat, maxDistance: Float = 100000f, numberToSelect: Int = 100): Float = {
    Math.max(0f, maxDistance - distance(descriptor1, descriptor2, numberToSelect))
  }

  def buildDescriptor(rows: Int, cols: Int, `type`: Int, data: IndexedSeq[Int]): Mat = {
    require(data.length == rows * cols, "Matrix length should equal rows * cols")
    require(`type` == 0, "Matrix element type should be CV_8U")

    `type` match {
      case 0 => // CV_8U type => UByteRawIndexer
        val mat = new Mat(rows, cols, `type`)
        val indexer = mat.createIndexer().asInstanceOf[UByteRawIndexer]

        for((value, index) <- data.zipWithIndex){
          indexer.put(0, 0, index, value)
        }

        mat
    }
  }

  def matToIndexedSeq(mat: Mat): IndexedSeq[Int] = {
    require(mat.`type`() == 0, "Matrix element type should be CV_8U")

    val indexer = mat.createIndexer().asInstanceOf[UByteRawIndexer]
    val size = mat.total.toInt
    for (i <- 0 until size) yield indexer.get(0, 0, i)
  }

  def distance(descriptor1: Mat, descriptor2: Mat, numberToSelect: Int): Float = {
    // Create feature matcher
    val matcher = new BFMatcher(NORM_HAMMING, false)
    val matches = new DMatchVector()

    matcher.`match`(descriptor1, descriptor2, matches)

    //compute distance over the best n matches
    distanceOfBestMatches(matches, numberToSelect)
  }

  private def loadOrExit(file: File, flags: Int = IMREAD_COLOR): Mat = {
    // Read input image
    val image = imread(file.getAbsolutePath, flags)
    //log("Computing descriptor of picture: " + file.getAbsolutePath)
    if (image.empty()) {
      error("Couldn't load image: " + file.getAbsolutePath)
    }
    image
  }

  private def computeDescriptor(imageMat: Mat): Mat = {
    val keyPoint = new KeyPointVector()
    val descriptor = new Mat()

    val orb = ORB.create(
      500, //nFeatures
      1.2f,  //scaleFactor
      8,  //nlevels
      31,  //edgeThreshold
      0,  //firstLevel
      2,  //WTA_K
      ORB.HARRIS_SCORE, //scoreType
      31, //patchSize
      20 //fastThreshold
    )

    // Detect ORB features and compute descriptors for both images
    orb.detect(imageMat, keyPoint)
    orb.compute(imageMat, keyPoint, descriptor)

    descriptor
  }

  private def distanceOfBestMatches(matches: DMatchVector, numberToSelect: Int): Float = {
    // Convert to Scala collection, and sort
    val sorted = toIndexedSeq(matches)//.sortWith(_ lessThan _)
    if(sorted.size < numberToSelect){
      error(s"Number of matches (${sorted.size}) is smaller than numberToSelect ($numberToSelect)")
    }
    sorted.map(`match` => `match`.distance()).sortWith(_ < _).take(numberToSelect).sum
  }

  private def toIndexedSeq(matches: DMatchVector): IndexedSeq[DMatch] = {
    // for the simplicity of the implementation we will assume that number of key points is within Int range.
    require(matches.size() <= Int.MaxValue)
    val n = matches.size().toInt

    // Convert keyPoints to Scala sequence
    for (i <- 0 until n) yield new DMatch(matches.get(i))
  }

}
