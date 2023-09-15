using System.Collections;
using System.Collections.Generic;
using UnityEngine;

public class ClickCapture : MonoBehaviour
{
    public delegate void OnGestureCallback(Vector2 startPos, Vector2 endPos, float duration, PhoneChannel.GestureType willContinue);
    public OnGestureCallback gestureCallback;
    public OnGestureCallback pinchCallback;
    private Plane screenPlane;
    private bool wasDown = false;
    private long tStart;
    private Vector2 pStart;
    private Vector2[] screenCorners;
    public float scrollScale = 1;
    private float totalScroll = 0;
    private Vector2 scrollPos;
    private int doneScrolling = 0;
    public long contiGestureThreshold = 400;

    // Start is called before the first frame update
    void Start()
    {
        // Clockwise
        screenCorners = new Vector2[4];
        screenCorners[0] = new Vector2(transform.position.x + transform.localScale.x / 2, transform.position.y + transform.localScale.y / 2);
        screenCorners[1] = new Vector2(transform.position.x + transform.localScale.x / 2, transform.position.y - transform.localScale.y / 2);
        screenCorners[2] = new Vector2(transform.position.x - transform.localScale.x / 2, transform.position.y - transform.localScale.y / 2);
        screenCorners[3] = new Vector2(transform.position.x - transform.localScale.x / 2, transform.position.y + transform.localScale.y / 2);

        screenPlane = new Plane(Vector3.back, transform.position.z);
    }

    // Update is called once per frame
    void Update()
    {
        CheckGestureStarted();
        if (!CheckGestureCompleted())
        {
            if (CheckContiGesture()) 
                Debug.Log("Dispatching Intermediate Gesture");
        }
        else Debug.Log("Finished gesture");

        CheckScroll();
    }

    private void CheckScroll()
    {
        Vector2 scroll = Input.mouseScrollDelta;

        if (scroll.y != 0)
        {
            // Check if we're on the phone screen
            Ray ray = Camera.main.ScreenPointToRay(Input.mousePosition);
            if (screenPlane.Raycast(ray, out float distance))
            {
                Vector3 mp = ray.GetPoint(distance);
                if (IsInside(mp))
                {
                    // User is scrolling. Accumulate.
                    totalScroll += scroll.y * scrollScale;

                    // Screen position of the scrolling
                    scrollPos = mp;

                    // Currently scrolling 
                    doneScrolling = 0;
                }
            }
            else doneScrolling++;
        } else if (Mathf.Abs(totalScroll) > 1)
        {
            doneScrolling++;
        }

        if (doneScrolling > 100)
        {
            // User is done scrolling. Send and zero
            Vector2 v2 = Vector2.zero;
            v2.x = (totalScroll > 0) ? 1 : 0;
            scrollPos = new Vector2((scrollPos.x - transform.position.x + transform.localScale.x / 2) / transform.localScale.x, (scrollPos.y - transform.position.y + transform.localScale.y / 2) / transform.localScale.y);
            pinchCallback(scrollPos, v2, Mathf.Abs(totalScroll), PhoneChannel.GestureType.Pinch);
            scrollPos = Vector2.zero;
            totalScroll = 0;
            doneScrolling = 0;
        }
    }

    private bool CheckGestureStarted()
    {
        // Gesture started
        if (!wasDown && Input.GetMouseButtonDown(0))
        {
            Ray ray = Camera.main.ScreenPointToRay(Input.mousePosition);

            if (screenPlane.Raycast(ray, out float distance))
            {
                Vector3 mp = ray.GetPoint(distance);
                if (IsInside(mp))
                {
                    wasDown = true;
                    tStart = Timer.GetTimeUs();
                    pStart = new Vector2(mp.x, mp.y);
                    return true;
                }
            }
        }
        return false;
    }

    private bool CheckGestureCompleted()
    {
        // Gesture completed
        if (wasDown && !Input.GetMouseButton(0))
        {
            wasDown = false;
            long tEnd = Timer.GetTimeUs();

            Ray ray = Camera.main.ScreenPointToRay(Input.mousePosition);
            if (screenPlane.Raycast(ray, out float distance))
            {
                Vector3 mp = ray.GetPoint(distance);
                Vector2 pEnd = new Vector2(mp.x, mp.y);

                if (!IsInside(mp))
                {
                    Vector2 dp = pEnd - pStart;
                    float[,] A = new float[2, 2];
                    float[,] Ainv = new float[2, 2];
                    A[0, 0] = dp.x; A[1, 0] = dp.y;

                    // If gesture ended off the phone screen, project back to the screen
                    for (int i = 0; i < 4; i++)
                    {
                        Vector2 dv = screenCorners[(i + 1) % 4] - screenCorners[i];
                        Vector2 pmv = screenCorners[i] - pStart;
                        A[0, 1] = -dv.x; A[1, 1] = -dv.y;

                        // Check determinant of A
                        float detA = A[0, 0] * A[1, 1] - A[0, 1] * A[1, 0];
                        if (detA != 0)
                        {
                            // Calculate inverse
                            Ainv[0, 0] = A[1, 1] / detA;
                            Ainv[1, 1] = A[0, 0] / detA;
                            Ainv[0, 1] = -A[0, 1] / detA;
                            Ainv[1, 0] = -A[1, 0] / detA;

                            // Calculate intersection
                            float theta = Ainv[0, 0] * pmv[0] + Ainv[0, 1] * pmv[1];
                            float phi = Ainv[1, 0] * pmv[0] + Ainv[1, 1] * pmv[1];

                            // Check if this intersection is actually the edge of the screen
                            if (theta <= 1 && theta >= 0 && phi <= 1 && phi >= 0)
                            {
                                pEnd = pStart + theta * dp;
                                break;
                            }
                        }
                    }
                }
                // Make start and finish positions relative to screen size, centred at the screen centre
                pStart = new Vector2((pStart.x - transform.position.x + transform.localScale.x / 2) / transform.localScale.x, (pStart.y - transform.position.y + transform.localScale.y / 2) / transform.localScale.y);
                pEnd = new Vector2((pEnd.x - transform.position.x + transform.localScale.x / 2) / transform.localScale.x, (pEnd.y - transform.position.y + transform.localScale.y / 2) / transform.localScale.y);
                gestureCallback(pStart, pEnd, (tEnd - tStart) / 1000.0f, PhoneChannel.GestureType.Normal);
                return true;
            }
        }
        return false;
    }

    private bool CheckContiGesture()
    {
        long t = Timer.GetTimeUs();
        // Gesture not completed, but enough time has elapsed
        if (wasDown && Input.GetMouseButton(0) && t - tStart > 1000 * contiGestureThreshold)
        {
            Ray ray = Camera.main.ScreenPointToRay(Input.mousePosition);
            if (screenPlane.Raycast(ray, out float distance))
            {
                Vector3 mp = ray.GetPoint(distance);
                Vector2 pEnd = new Vector2(mp.x, mp.y);

                if (!IsInside(mp))
                {
                    Vector2 dp = pEnd - pStart;
                    float[,] A = new float[2, 2];
                    float[,] Ainv = new float[2, 2];
                    A[0, 0] = dp.x; A[1, 0] = dp.y;

                    // If gesture ended off the phone screen, project back to the screen
                    for (int i = 0; i < 4; i++)
                    {
                        Vector2 dv = screenCorners[(i + 1) % 4] - screenCorners[i];
                        Vector2 pmv = screenCorners[i] - pStart;
                        A[0, 1] = -dv.x; A[1, 1] = -dv.y;

                        // Check determinant of A
                        float detA = A[0, 0] * A[1, 1] - A[0, 1] * A[1, 0];
                        if (detA != 0)
                        {
                            // Calculate inverse
                            Ainv[0, 0] = A[1, 1] / detA;
                            Ainv[1, 1] = A[0, 0] / detA;
                            Ainv[0, 1] = -A[0, 1] / detA;
                            Ainv[1, 0] = -A[1, 0] / detA;

                            // Calculate intersection
                            float theta = Ainv[0, 0] * pmv[0] + Ainv[0, 1] * pmv[1];
                            float phi = Ainv[1, 0] * pmv[0] + Ainv[1, 1] * pmv[1];

                            // Check if this intersection is actually the edge of the screen
                            if (theta <= 1 && theta >= 0 && phi <= 1 && phi >= 0)
                            {
                                pEnd = pStart + theta * dp;
                                break;
                            }
                        }
                    }
                }
                // Make start and finish positions relative to screen size, centred at the screen centre
                Vector2 ps = new Vector2((pStart.x - transform.position.x + transform.localScale.x / 2) / transform.localScale.x, (pStart.y - transform.position.y + transform.localScale.y / 2) / transform.localScale.y);
                pEnd = new Vector2((pEnd.x - transform.position.x + transform.localScale.x / 2) / transform.localScale.x, (pEnd.y - transform.position.y + transform.localScale.y / 2) / transform.localScale.y);
                gestureCallback(ps, pEnd, (t - tStart) / 1000.0f, PhoneChannel.GestureType.WillContinue);
                tStart = t;
                pStart = pEnd;
                return true;
            }
        }
        return false;
    }

    private bool IsInside(Vector3 p)
    {
        return p.x <= screenCorners[0].x && p.x >= screenCorners[2].x && p.y >= screenCorners[2].y && p.y <= screenCorners[0].y; 
    }
}
